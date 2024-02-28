5 integer x, y
10 println "pop and push test"
15 integer b
18 let b = 23
20 push 50*3 + b
30 integer a
40 pop a
50 println "a=" , a
60 push a
70 pop b
80 println "b=",b
90 push 5
100 push 7
110 gosub 200
11 push 2
12 push 4
13 gosub 200
120 end
200 println "in sub"
210 pop y
220 pop x
230 println "x+y=", x+y
240 ret
250 end
