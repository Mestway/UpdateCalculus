/*************************************************************************
	> File Name: CodeRefactoring.java
	> Author: Stanley Wong
	> Mail: stangley.chenglongwang@gmail.com 
	> Created Time: Sun 20 Apr 2014 01:56:21 AM PDT
 ************************************************************************/

package polyglot.ext.update.visit;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.ext.update.ast.*;
import polyglot.ext.update.match.Matching;
import polyglot.frontend.*;
import polyglot.types.*;
import polyglot.visit.NodeVisitor;
import polyglot.ext.update.match.TypeName;
import polyglot.ext.update.match.Pair;
import polyglot.ext.update.match.JavaBody;
import polyglot.ext.update.match.MatchChecker;

public class CodeRefactoring extends NodeVisitor
{
	NodeFactory nf;
	String fileName = "DONOTUSETHISNAME.DONOTUSETHISNAME";
	ArrayList<Matching> rawMatching = new ArrayList<Matching>();
	Context context;

	// This is used for the TypedTranslator
	TypeSystem ts;
	TargetFactory tf;
	UpdateTypedTranslator updateTypedTranslator = null;
	// put the dummy classes into the list
	ArrayList<String> dummyClasses = new ArrayList<String>();

	public CodeRefactoring(Job job, TypeSystem ts, NodeFactory nf, TargetFactory tf) {	
		this.context = job.context();
		this.nf = nf;
		this.ts = ts;
		this.tf = tf;

		updateTypedTranslator = new UpdateTypedTranslator(job, ts, nf, tf);
		
		System.out.println("Code Refactoring Begin");
		
		File file = new File(fileName);
		BufferedReader reader = null;
		System.out.println(file.getAbsolutePath());
	
		String inputString = new String();
		String dummyString = new String();

		try {
			reader = new BufferedReader(new FileReader(file));
			
			String tempString = null;
			
			while ((tempString = reader.readLine()) != null) {
				if (tempString.startsWith("#"))
					dummyString += tempString;
				else if (tempString.startsWith("//"))
					continue;
				else
					inputString += tempString;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		String pattern = "\\[[^\\[\\]]*\\]";
		Pattern r = Pattern.compile(pattern);
		Matcher matcher = r.matcher(inputString);
		while (matcher.find()) {
			String oneMatching = matcher.group();
			rawMatching.add(new Matching(oneMatching));	
		}

		pattern = "#[^#]++";
		r = Pattern.compile(pattern);
		matcher = r.matcher(dummyString);
		while (matcher.find()) {
			String tmpStr = matcher.group();
			dummyClasses.add(tmpStr.substring(1,tmpStr.length()));
		}	
	
		// Fill in the dummy classes with their arguments type list
		for (Matching iMatch : rawMatching) {
			for (String iDummy : dummyClasses) {
				if (iMatch.getTypePair().second().equals(iDummy)) {
					if (iMatch.getBlockPair().second().isNew()) {
						ArrayList<String> dummyArgs = new ArrayList<String>();
						for (String iArg : iMatch.getBlockPair().second().getArgs().get(0)) {
							Pair<TypeName> pr = iMatch.defLookupDst(iArg);
							dummyArgs.add(pr.second().getType());
						}
						updateTypedTranslator.addDummy(iDummy, dummyArgs);
					}
				}
			}
		}

		// check if the matching rules are qualified
		MatchChecker matchChecker = new MatchChecker(rawMatching);
		matchChecker.setDummyClasses(updateTypedTranslator.getDummyClasses(), updateTypedTranslator.getDummyArgs());

		//updateTypedTranslator.printDummy();
	}

	public UpdateTypedTranslator getTranslator() {
		return updateTypedTranslator;
	}

	@Override
	public NodeVisitor enter(Node n) {
		
		// Check if the typeNames presented in mathcing is ambiguious.
		if (ClassTypeString.Visited == false) {
			ClassTypeString.printMap();
			ClassTypeString.Visited = true;
			for (Matching match : rawMatching) {
				if (ClassTypeString.bannedName().contains(match.getTypePair().first())
				||	ClassTypeString.bannedName().contains(match.getTypePair().second())) {
					System.err.println("ShortName unable to identify");
					System.err.println(match.getTypePair().first() + " + " + match.getTypePair().second());
					System.exit(-1);
				}
			}
		}
		
		// Visit different Nodes
		if (n instanceof UpdateJL5CanonicalTypeNode_c) {
			typeNodeHandler((UpdateJL5CanonicalTypeNode_c)n);
		} else if (n instanceof UpdateJL5Call_c) {
			callNodeHandler((UpdateJL5Call_c)n);
		} else if (n instanceof UpdateJL5New_c) {
			newNodeHandler((UpdateJL5New_c)n);
		}
		return this;
	}

	// Deal with UpdateJL5CanonicalTypeNode_c
	protected void typeNodeHandler(UpdateJL5CanonicalTypeNode_c node) {
		String outputName = null;
		ClassTypeString classType = parseClassType(node.toString());
		
		for (Matching match : rawMatching) {
			classType = classType.processMatching(match);
		}
		outputName = classType.toTypeString();
		node.setOutputName(outputName);
	}

	// Deal with UpdateJL5Call_c
	protected void callNodeHandler(UpdateJL5Call_c node) {
		String outputName = null;
		try {
			ReferenceType targetType = node.findTargetType();
			ClassTypeString targetClassType = parseClassType(targetType.toString());
	
			for	(Matching match : rawMatching) {
				ArrayList<Pair<TypeName>> defPairs = match.getDefPairs();
				Pair<JavaBody> blockPair = match.getBlockPair();
			
				Pair<TypeName> targetPair = match.defLookUp(blockPair.first().getTarget());
				

				String tttmpS = match.getBlockPair().first().getMethodName().get(0);
				
				String srcMatchType = new String();

				// if targetPair == null , then this matching may be a JL5New without arguments
				//							or a Class as the argument(just copy it)
				if (targetPair == null) {
					if (match.getBlockPair().first().isNew())
						continue;
					else {
						srcMatchType = blockPair.first().getTarget();
					}
					
				} else {
					srcMatchType = targetPair.first().getType();
				}
					
				if (ClassTypeString.classTypeCompare(srcMatchType, targetClassType.typeName())) {

					JavaBody srcJavaBody = match.getBlockPair().first();
					JavaBody dstJavaBody = match.getBlockPair().second();
					String srcMethodName = srcJavaBody.getMethodName().get(0);
			
					if (node.name().equals(srcMethodName)) {
						//String dstMethodName = parseMethodInvoke(match.getBlockPair().second().allString()); 
						node.setMatch(match);	
					}		
				}
			}
		} catch (SemanticException e) {
			System.err.println("JL5Call_c Node Type cannot find: " + node.toString());	
		}

	}

	// Deal with UpdateJL5New_c
	protected void newNodeHandler(UpdateJL5New_c node) {
		String outputName = null;
		ClassTypeString classType = parseClassType(node.objectType().toString());
		
		Matching destinedMatch = null; 
		for (Matching match : rawMatching) {
			if (classType.meetMatch(match)) {
				destinedMatch = match;
				break;
			}	
		}

		node.setMatch(destinedMatch);
		for (Matching match : rawMatching) {
			classType = classType.processMatching(match);
		}
		
		outputName = classType.toTypeString();
		node.setOutputName(outputName);
	}

	// WARNING : this is replaced by parseClassType2
	// Parse a string represent class type
	// and store it into ClassTypeString
	// e.g. ArrayList<Integer> will be stored in a ClassTypeString
	protected ClassTypeString parseClassType(String type) {
		return parseClassType2(type);
	}

	protected ClassTypeString parseClassType2(String type) {
		int firstI = type.indexOf("<");
		int lastI = type.lastIndexOf(">");

		if (firstI == -1 && lastI == -1) {
			ClassTypeString strClassType = new ClassTypeString();
			strClassType.typeName(type);
			return strClassType;
		}

		String typeName = type.substring(0, firstI);
		String args = type.substring(firstI + 1, lastI);

		ClassTypeString classType = new ClassTypeString();
		classType.typeName = (typeName);
		ArrayList<String> typeArgs = splitByComma(args);
		for (String str : typeArgs) {
			if (str.indexOf('<') == -1 && str.indexOf('>') == -1) {
				classType.typeArgs().add(str);
			} else {
				ClassTypeString arg = parseClassType2(str);
				classType.typeArgs().add(arg);
			}
		}
		
		return classType;
	}

	protected String parseMethodName(String type) {
		String[] parts = type.split("\\.");
		return parts[parts.length - 1];
	}

	

	// input a string and return an arrayList without comma outside 
	protected ArrayList<String> splitByComma(String input) {
		int count = 0;
		ArrayList<String> array = new ArrayList<String>();
		for (int i = input.length() - 1; i >= 0; i --) {
			if (input.charAt(i) == ',' && count == 0) {
				array.add(input.substring(i+1,input.length()));
				input = input.substring(0,i);
			} else if (input.charAt(i) == '>') {
				count ++;
			} else if (input.charAt(i) == '<') {
				count --;
			}
		}

		array.add(input);
		ArrayList<String> result = new ArrayList<String>();
		for (int i = array.size() - 1; i >= 0; i --) {
			result.add(array.get(i));
		}
		return result;
	}

	protected String parseMethodInvoke(String srcMethod) {
		
		String method = srcMethod.substring(srcMethod.indexOf('.')+1,srcMethod.length());

		ArrayList<String> methodSeq = new ArrayList<String>();

		String regex = "[^\\(\\)]*";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(method);
		while (matcher.find()) {
			String methodName = matcher.group();
			if(!methodName.equals(""))
				methodSeq.add(methodName);
		}
		
		if (methodSeq.size() == 0)
			return "";

		// TODO --> maybe bugs with arguments
		String outputName = new String();
		for (int i = 0; i <= methodSeq.size() - 2; i ++) {
			outputName += methodSeq.get(i) + "()";
		}
		outputName += methodSeq.get(methodSeq.size()-1);
	
		return outputName;
	}

}