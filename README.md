This is an implementation of Java Update Calculus.

Input
============================================================================
To run a example, you need two inputs:
	1. MatchInfo.in
	2. input source code

1. MatchInfo.in
The Matching should be input as MatchInfo.in in the directory.
The matching grammar consists of three parts:
	[T1 (D1) {B1} T2 (D2) {B2}] (This should be put in a line.)
e.g.
	[Enumeration (Vector v) {v.elements()} Iterator (ArrayList a) {a.iterator()}]

2. Input source code
This can be put anywhere, but the input source file should have the suffix .update
e.g.  TestCase.update


Run
============================================================================

To run the test case, you need to use ./test in the base directory. The grammar is like the following:

./test input1.update input2.update input3.update .......


Output
============================================================================
The output will be displayed in the output directory.



