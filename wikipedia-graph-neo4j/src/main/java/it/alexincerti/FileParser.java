package it.alexincerti;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileParser {

	public static List<String> getPages(String line) {
		Pattern pattern = Pattern.compile("((?<=\\A|,)\\(\\d.*?[\\dL]\\)(?=,|\\z))+");
		List<String> pages = new ArrayList<>();

		line = line.replace("INSERT INTO `page` VALUES ", "");

		Matcher matcher = pattern.matcher(line);
		String page = null;
		while (matcher.find()) {
			page = matcher.group();
			if (page.startsWith("(")) {
				page = page.substring(1, page.length());
			}
			if (page.endsWith(")")) {
				page = page.substring(0, page.length() - 1);
			}
			pages.add(page);
		}
		return pages;
	}

	public static String getPageName(String page) {
		Pattern namePattern = Pattern.compile("([\"'])(?:(?=(\\\\?))\\2.)*?\\1");
		String name = null;
		Matcher nameMatcher = namePattern.matcher(page);
		if (nameMatcher.find()) {
			name = nameMatcher.group();
			name = name.replaceAll("^\'", "");
			if (name.endsWith("'")) {
				name = name.substring(0, name.length() - 1);
			}
		}
		if (name == null && page.contains("14,")) {
			// System.err.println("Returning null " + page);
		}
		return name;
	}

}
