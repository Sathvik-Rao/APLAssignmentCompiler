import java.nio.file.Files;
import java.nio.file.Paths;
import java.beans.Statement;
import java.io.IOException;
import java.lang.reflect.Member;
import java.util.regex.*;

import javax.lang.model.util.ElementScanner6;
import javax.security.sasl.SaslClientFactory;

import java.util.*;

class Parse {
    static String regexString = "\"[^\"]*\"",
            regexWS = "[ \\t]*",
            regexID = "[a-zA-Z_][a-zA-Z0-9_]*",
            regexExpr = "[a-zA-Z0-9(-+_ \\t/-]+",
            regexExprStandalone = "[a-zA-Z0-9 \\t]+=?[a-zA-Z0-9(-+_ \\t/-]*",
            regexINT = "[0-9]+",
            regexLineNumber = "(" + regexWS + regexINT + ")([ \\t].*)?",
            regexIfThen = regexWS + "IF[ \\t]" + regexWS + "(" + regexExpr + ")([<>!=])(" + regexExpr + ")THEN (.*)",
            regexPrint = regexWS + "PRINT((" + regexWS + regexString + regexWS + "|[ \\t]+" + regexExpr + ")"
                    + "(," + regexWS + regexString + regexWS + "|," + regexWS + regexExpr + ")*)",
            regexPrintLn = regexWS + "PRINTLN((" + regexWS + regexString + regexWS + "|[ \\t]+" + regexExpr + ")"
                    + "(," + regexWS + regexString + regexWS + "|," + regexWS + regexExpr + ")*)";

    Scanner sc = new Scanner(System.in);
    Memory m;

    Parse(Memory mem) {
        m = mem;
    }

    void parseLine(String line) {
        if (validateRegex(line, regexPrint)) {
            // PRINT expr|"*" [,expr|"*"]*
            parsePrint(getRegex(line, regexPrint, 1), line);
        } else if (validateRegex(line, regexPrintLn)) {
            // PRINTLN expr|"*" [,expr|"*"]*
            parsePrint(getRegex(line, regexPrintLn, 1), line);
            m.returnString += "\n";
        } else if (validateRegex(line,
                regexWS + "LET[ \\t]" + regexWS + regexID + regexWS + "=" + regexExpr)) {
            // LET ID = expr
            line = line.trim();
            String[] tokens = line.split("=");
            String key = tokens[0].substring(3).trim();
            if (m.memory.containsKey(key)) {
                ExpressionEvaluation obj = new ExpressionEvaluation(expressionStringToStringArray(tokens[1]), m);
                m.memory.put(key, obj.expr());
            } else {
                m.error("\nError[line=" + m.currentLine + "] '" + key + "' variable not defined");
            }
        } else if (validateRegex(line,
                regexWS + "INTEGER[ \\t]" + regexWS + regexID + "((" + regexWS + "," + regexWS + regexID
                        + regexWS + ")*)?" + regexWS)) {
            // INTEGER ID [,ID]*
            String[] variables = getRegex(line, "(" + regexWS + "INTEGER[ \\t])(.*)", 2).split(",");
            for (String ID : variables) {
                String key = ID.trim();
                if (m.memory.containsKey(key)) {
                    m.error("\nError[line=" + m.currentLine + "] '" + key + "' variable already defined");
                } else {
                    m.memory.put(key, null);
                }
            }
        } else if (validateRegex(line,
                regexWS + "INPUT[ \\t]" + regexWS + regexID + "((" + regexWS + "," + regexWS + regexID
                        + regexWS + ")*)?" + regexWS)) {
            // INPUT ID [,ID]*
            String[] variables = getRegex(line, "(" + regexWS + "INPUT[ \\t])(.*)", 2).split(",");
            String input = sc.nextLine();
            if (validateRegex(input, regexWS + "-?" + regexINT + "([ \\t]+-?" + regexINT + ")*" + regexWS)) {
                String[] inputs = input.trim().split("\\s+");
                if (inputs.length > variables.length) {
                    m.error("\nError[line=" + m.currentLine + "] input attributes overloaded");
                } else if (inputs.length < variables.length) {
                    m.error("\nError[line=" + m.currentLine + "] missing input values");
                }
                for (int i = 0; i < inputs.length; i++) {
                    if (!m.memory.containsKey(variables[i].trim())) {
                        m.error("\nError[line=" + m.currentLine + "] '" + variables[i].trim()
                                + "' variable not defined");
                    }
                    m.memory.put(variables[i].trim(), Integer.valueOf(inputs[i].trim()));
                }
            } else {
                m.error("\nError[line=" + m.currentLine + "] invalid input values");
            }
        } else if (validateRegex(line, regexWS + "GOTO[ \\t]" + regexWS + regexINT + regexWS)) {
            // GOTO line_number
            m.gotoStatement = true;
            m.currentLine = Integer
                    .valueOf(getRegex(line, regexWS + "GOTO[ \\t]" + regexWS + "(" + regexINT + ")" + regexWS, 1)
                            .trim());
        } else if (validateRegex(line, regexIfThen)) {
            // IF expression ‘=’ | ‘>’ |’ < ’| ’!’ expression THEN statement
            ExpressionEvaluation expr1 = new ExpressionEvaluation(
                    expressionStringToStringArray(getRegex(line, regexIfThen, 1)), m);
            ExpressionEvaluation expr2 = new ExpressionEvaluation(
                    expressionStringToStringArray(getRegex(line, regexIfThen, 3)), m);
            boolean condition = false;
            String ro = getRegex(line, regexIfThen, 2).trim();
            if (ro.equals("=")) {
                if (expr1.expr() == expr2.expr()) {
                    condition = true;
                }
            } else if (ro.equals(">")) {
                if (expr1.expr() > expr2.expr()) {
                    condition = true;
                }
            } else if (ro.equals("<")) {
                if (expr1.expr() < expr2.expr()) {
                    condition = true;
                }
            } else if (ro.equals("!")) {
                if (expr1.expr() != expr2.expr()) {
                    condition = true;
                }
            }
            if (condition) {
                m.extraStatement = getRegex(line, regexIfThen, 4).trim();
            }
        } else if (validateRegex(line, regexWS + "GOSUB[ \\t]" + regexWS + regexINT + regexWS)) {
            // GOSUB line_number
            m.gosubStatement = true;
            m.currentLine = Integer
                    .valueOf(getRegex(line, regexWS + "GOSUB[ \\t]" + regexWS + "(" + regexINT + ")" + regexWS, 1)
                            .trim());

        } else if (validateRegex(line, regexWS + "RET" + regexWS)) {
            // RET
            m.retStatement = true;
        } else if (validateRegex(line, regexWS + "PUSH[ \\t]" + regexExpr)) {
            // PUSH expr
            ExpressionEvaluation obj = new ExpressionEvaluation(
                    expressionStringToStringArray(getRegex(line, regexWS + "PUSH[ \\t](" + regexExpr + ")", 1)), m);
            m.memoryStack.push(obj.expr());
        } else if (validateRegex(line, regexWS + "POP[ \\t]" + regexWS + regexID + regexWS)) {
            // POP ID
            String key = getRegex(line, "(" + regexWS + "POP[ \\t])(.*)", 2).trim();
            if (m.memory.containsKey(key)) {
                if (m.memoryStack.size() == 0) {
                    m.error("\nError[line=" + m.currentLine + "] invalid pop operation empty stack exception");
                } else {
                    m.memory.put(key, m.memoryStack.pop());
                }
            } else {
                m.error("\nError[line=" + m.currentLine + "] '" + key + "' variable not defined");
            }
        } else if (validateRegex(line, regexWS + "END" + regexWS)) {
            // END
            m.stop();
        } else if (validateRegex(line, regexWS)) {
            // empty line do nothing
        } else {
            // undefined
            m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + line + "'");
        }
    }

    static boolean validateRegex(String line, String regex) {
        return Pattern.compile(regex).matcher(line).matches();
    }

    static String getRegex(String line, String regex, int grp) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            return matcher.group(grp);
        }
        return "";
    }

    void parsePrint(String token, String line) {
        ArrayList<String> list = new ArrayList<String>();
        boolean startQuote = false;
        int first = 0, i = 0;
        for (; i < token.length(); i++) {
            if (token.charAt(i) == '"') {
                startQuote = !startQuote;
            } else if (token.charAt(i) == ',') {
                if (!startQuote) {
                    list.add(token.substring(first, i));
                    first = i + 1;
                }
            }
        }
        list.add(token.substring(first, i));

        for (String item : list) {
            if (validateRegex(item, regexWS)) {
                m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + line + "'");
            } else if (validateRegex(item, regexWS + regexString + regexWS)) {
                // "*"
                String temp = getRegex(item, regexString, 0);
                m.returnString += temp.substring(1, temp.length() - 1);
            } else if (validateRegex(item, regexExpr)) {
                // expr
                ExpressionEvaluation obj = new ExpressionEvaluation(expressionStringToStringArray(item), m);
                int value = obj.expr();
                m.returnString += value;
            } else {
                m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + line + "'");
            }
        }
    }

    String[] expressionStringToStringArray(String line) {

        line = line.replaceAll("\\s", "");
        ArrayList<String> strList = new ArrayList<String>();
        String temp = "";
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '+' || line.charAt(i) == '-' || line.charAt(i) == '*' || line.charAt(i) == '/'
                    || line.charAt(i) == '=' || line.charAt(i) == '(' || line.charAt(i) == ')') {
                if (temp.length() > 0) {
                    strList.add(temp);
                }
                strList.add(line.substring(i, i + 1));
                temp = "";
            } else {
                temp += line.charAt(i);
            }
        }
        if (temp.length() > 0) {
            strList.add(temp);
        }
        String[] strArray = strList.toArray(new String[0]);
        return strArray;
    }
}

class ExpressionEvaluation {
    String[] s = null;
    Memory m;
    int arrayPointer = 0;

    ExpressionEvaluation(String[] str, Memory mem) {
        s = str;
        m = mem;
    }

    int expr() {

        int val = multExpr();
        while (true) {
            if (arrayPointer < s.length && s[arrayPointer].equals("+")) {
                arrayPointer++;
                val += multExpr();
            } else if (arrayPointer < s.length && s[arrayPointer].equals("-")) {
                arrayPointer++;
                val -= multExpr();
            } else {
                if (arrayPointer < s.length) {
                    m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + s[arrayPointer]
                            + "'");
                }
                break;
            }
        }
        return val;
    }

    int multExpr() {

        int val = atom();
        while (true) {
            if (arrayPointer < s.length && s[arrayPointer].equals("*")) {
                arrayPointer++;
                val *= atom();
            } else if (arrayPointer < s.length && s[arrayPointer].equals("/")) {
                arrayPointer++;
                val /= atom();
            } else {
                break;
            }
        }
        return val;
    }

    int atom() {

        if (arrayPointer >= s.length) {
            m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + s[arrayPointer - 1]
                    + "'");
            return 0;
        } else if (Parse.validateRegex(s[arrayPointer], Parse.regexINT)) {
            // INT
            return Integer.valueOf(s[arrayPointer++]);
        } else if ((arrayPointer == 0 && s[arrayPointer].equals("("))
                || (arrayPointer > 0 && s[arrayPointer].equals("(")
                        && Parse.validateRegex(s[arrayPointer - 1], "[=*/+-]+"))) {
            // '(' expr ')'
            int count = 1, j = arrayPointer + 1;
            for (; j < s.length; j++) {
                if (s[j].equals("(")) {
                    count++;
                } else if (s[j].equals(")")) {
                    count--;
                }
                if (count == 0) {
                    break;
                }
            }
            String[] temp = Arrays.copyOfRange(s, arrayPointer + 1, j);
            arrayPointer = j + 1;
            ExpressionEvaluation obj = new ExpressionEvaluation(temp, m);
            return obj.expr();
        } else if (Parse.validateRegex(s[arrayPointer], Parse.regexID)) {
            // ID
            String key = s[arrayPointer++];
            Integer v = m.memory.get(key);
            if (v != null) {
                return v.intValue();
            } else {
                m.error("\nError[line=" + m.currentLine + "] '" + key + "' variable not defined");
                return 0;
            }
        } else {
            // undefined
            m.error("\nError[line=" + m.currentLine + "] No viable alternative at input '" + s[arrayPointer++]
                    + "'");
            return 0;
        }

    }
}

class Stack {
    ArrayList<Integer> list;

    Stack() {
        list = new ArrayList<Integer>();
    }

    void push(int value) {
        list.add(value);
    }

    int pop() {
        if (list.size() == 0) {
            return -1;
        }
        return list.remove(list.size() - 1);
    }

    int size() {
        return list.size();
    }
}

class Memory {
    class LineMemory implements Comparable<LineMemory> {
        int lineNumber;
        String line;

        @Override
        public int compareTo(LineMemory lm) {
            return Integer.compare(lineNumber, lm.lineNumber);
        }
    }

    ArrayList<LineMemory> program = new ArrayList<LineMemory>();
    int currentLine;
    HashMap<String, Integer> memory;
    String returnString, extraStatement;
    boolean gotoStatement, gosubStatement, retStatement;
    Stack callStack, memoryStack;

    Memory(String[] lines) {
        memory = new HashMap<>();
        returnString = "";
        extraStatement = null;
        currentLine = -1;
        gotoStatement = false;
        gosubStatement = false;
        retStatement = false;
        callStack = new Stack();
        memoryStack = new Stack();

        for (int i = 0; i < lines.length; i++) {
            if (Parse.validateRegex(lines[i], Parse.regexLineNumber)) {
                LineMemory temp = new LineMemory();
                temp.lineNumber = Integer.valueOf(Parse.getRegex(lines[i],
                        Parse.regexLineNumber, 1).trim());
                String statement = Parse.getRegex(lines[i], Parse.regexLineNumber, 2);
                if (statement == null) {
                    temp.line = "";
                } else {
                    if (statement.contains("\"")) {
                        int firstIndex = statement.indexOf("\"");
                        int lastIndex = statement.lastIndexOf("\"");
                        statement = statement.substring(0, firstIndex).toUpperCase()
                                + statement.substring(firstIndex, lastIndex + 1)
                                + statement.substring(lastIndex + 1).toUpperCase();
                        temp.line = statement.trim();
                    } else {
                        temp.line = statement.trim().toUpperCase();
                    }

                }
                program.add(temp);
            } else if (Parse.validateRegex(lines[i], Parse.regexWS)) {
                // do nothing
            } else {
                System.out.println("\nError parsing \"line number\" from '" + lines[i] + "'");
                break;
            }
        }
    }

    void start() {
        Parse obj = new Parse(this);
        for (int i = 0; i < program.size(); i++) {
            returnString = "";
            currentLine = program.get(i).lineNumber;
            obj.parseLine(program.get(i).line);

            while (extraStatement != null) {
                // if then statement
                String temp = extraStatement;
                extraStatement = null;
                obj.parseLine(temp);
            }
            if (gotoStatement) {
                // goto linenumber
                gotoStatement = false;
                i = getIndex(i) - 1;
                if (i < 0) {
                    error("\nError[line=" + currentLine + "] can't find line number '" + currentLine + "'");
                }
            }
            if (gosubStatement) {
                // gosub linenumber
                gosubStatement = false;
                if (callStack.size() > 256) {
                    error("\nError[line=" + currentLine + "] exceeded nested calls to subroutine [MAX=256]");
                }
                callStack.push(i + 1);
                i = getIndex(i) - 1;
                if (i < 0) {
                    error("\nLine number not found - " + currentLine);
                }
            }
            if (retStatement) {
                // ret
                retStatement = false;
                i = callStack.pop() - 1;
                if (i < 0) {
                    error("\nError[line=" + currentLine + "] invalid 'ret' statement");
                }
            }
            System.out.print(returnString);
        }
    }

    void stop() {
        System.exit(0);
    }

    void error(String error) {
        System.out.println(error);
        stop();
    }

    int getIndex(int currentIndex) {
        int fromIndex, toIndex;
        if (currentLine < program.get(currentIndex).lineNumber) {
            fromIndex = 0;
            toIndex = currentIndex;
        } else {
            fromIndex = currentIndex;
            toIndex = program.size();
        }
        LineMemory temp = new LineMemory();
        temp.lineNumber = currentLine;
        return Arrays.binarySearch(program.toArray(), fromIndex, toIndex, temp);
    }
}

class APLAssignmentCompiler {
    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            String content = new String(Files.readAllBytes(Paths.get(args[0])));
            String[] lines = content.split(System.lineSeparator());
            Memory m = new Memory(lines);
            if (m.program.size() > 0) {
                m.start();
            }
        } else {
            System.out.println("empty file name");
        }
    }
}