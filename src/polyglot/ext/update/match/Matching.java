package polyglot.ext.update.match;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

/*************************************************************************
	> File Name: match/Matching.java
	> Author: Stanley Wang
	> Mail: stanley.chenglongwang@gmail.com 
	> Created Time: Wed 23 Apr 2014 08:01:35 AM PDT
 ************************************************************************/

public class Matching {
	
	protected Pair<String> typePair =  new Pair<String>();	
	protected ArrayList<Pair<TypeName>> defPairs = new ArrayList<Pair<TypeName>>();
	protected Pair<JavaBody> blockPair = new Pair<JavaBody>();

	private int passNo = 0;
	private int secondPassCount = 0;

	public Matching(String rawMatching) {
		rawMatching = rawMatching.substring(rawMatching.indexOf("[") + 1, rawMatching.indexOf("]"));
		
		// Match the old and new one in a MatchingCommand
		String pattern = "\\w+\\s+\\([^\\(\\)]*\\)\\s+\\{[^\\{\\}]*\\}";
		Pattern r = Pattern.compile(pattern);
		Matcher matcher = r.matcher(rawMatching);

		while(matcher.find()) {

			// Parse the block
			String partString = matcher.group();
			String tempRegex = "\\{[^\\{\\}]*\\}";
			Pattern tempPattern = Pattern.compile(tempRegex);
			Matcher tempMatcher = tempPattern.matcher(partString);
			tempMatcher.find();
			String block = tempMatcher.group();
			blockPair.add(new JavaBody(block.substring(1,block.length()-1)));
			partString = partString.substring(0, tempMatcher.start());
		
			// Parse the definition
			tempRegex = "\\([^\\(\\)]*\\)";
			tempPattern = Pattern.compile(tempRegex);
			tempMatcher = tempPattern.matcher(partString);
			tempMatcher.find();
			String def = tempMatcher.group();
			String[] defParts = def.substring(1, def.length()-1).split(",");
			for (String oneDef : defParts) {
				String[] oneDefParts = oneDef.split(" ");
				boolean first = true;
				TypeName tpnm = new TypeName();
				for (String t : oneDefParts) {
					if (!t.equals("")) {
						if (first) {
							tpnm.setType(t);
							first = false;
						} else {
							tpnm.setName(t);
						}
					}
				}
				if (passNo == 0) {
					Pair<TypeName> onlyOneDef = new Pair<TypeName>();
					onlyOneDef.add(tpnm);
					defPairs.add(onlyOneDef);
				} else if (passNo == 1) {
					defPairs.get(secondPassCount).add(tpnm);
					secondPassCount ++;
				}
			}

			partString = partString.substring(0, tempMatcher.start());
			
			tempRegex = "\\S+";
			tempPattern = Pattern.compile(tempRegex);
			tempMatcher = tempPattern.matcher(partString);
			tempMatcher.find();
			typePair.add(tempMatcher.group());
		
			passNo ++;
		}
	
		

		/*typePair.print();
		defPair.print();
		blockPair.print();
		System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
		for (Pair<TypeName> pair : defPairs) {
			System.out.println("Wula: " + pair.first());
			System.out.println("Heis: " + pair.second());
		}*/
	}

	public Pair<String> getTypePair() {
		return typePair;
	}

	public Pair<TypeName> getFirstDefPair() {
		if (defPairs.get(0) != null)
			return defPairs.get(0);
		else return (new Pair<TypeName>());
	}

	public ArrayList<Pair<TypeName>> getDefPairs() {
		return defPairs;
	}

	public Pair<JavaBody> getBlockPair() {
		return blockPair;
	}
}
