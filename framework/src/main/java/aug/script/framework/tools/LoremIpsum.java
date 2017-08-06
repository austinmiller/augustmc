package aug.script.framework.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@SuppressWarnings("all")
public class LoremIpsum {
    private static String standard = "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor " +
            "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
            "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit " +
            "esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa " +
            "qui officia deserunt mollit anim id est laborum.";

    private static List<String> lipsumwords = new ArrayList<>(Arrays.asList(
            "a", "ac", "accumsan", "ad", "adipiscing", "aenean", "aliquam", "aliquet",
            "amet", "ante", "aptent", "arcu", "at", "auctor", "augue", "bibendum",
            "blandit", "class", "commodo", "condimentum", "congue", "consectetur",
            "consequat", "conubia", "convallis", "cras", "cubilia", "cum", "curabitur",
            "curae", "cursus", "dapibus", "diam", "dictum", "dictumst", "dignissim",
            "dis", "dolor", "donec", "dui", "duis", "egestas", "eget", "eleifend",
            "elementum", "elit", "enim", "erat", "eros", "est", "et", "etiam", "eu",
            "euismod", "facilisi", "facilisis", "fames", "faucibus", "felis",
            "fermentum", "feugiat", "fringilla", "fusce", "gravida", "habitant",
            "habitasse", "hac", "hendrerit", "himenaeos", "iaculis", "id", "imperdiet",
            "in", "inceptos", "integer", "interdum", "ipsum", "justo", "lacinia",
            "lacus", "laoreet", "lectus", "leo", "libero", "ligula", "litora",
            "lobortis", "lorem", "luctus", "maecenas", "magna", "magnis", "malesuada",
            "massa", "mattis", "mauris", "metus", "mi", "molestie", "mollis", "montes",
            "morbi", "mus", "nam", "nascetur", "natoque", "nec", "neque", "netus",
            "nibh", "nisi", "nisl", "non", "nostra", "nulla", "nullam", "nunc", "odio",
            "orci", "ornare", "parturient","pellentesque", "penatibus", "per",
            "pharetra", "phasellus", "placerat", "platea", "porta", "porttitor",
            "posuere", "potenti", "praesent", "pretium", "primis", "proin", "pulvinar",
            "purus", "quam", "quis", "quisque", "rhoncus", "ridiculus", "risus",
            "rutrum", "sagittis", "sapien", "scelerisque", "sed", "sem", "semper",
            "senectus", "sit", "sociis", "sociosqu", "sodales", "sollicitudin",
            "suscipit", "suspendisse", "taciti", "tellus", "tempor", "tempus",
            "tincidunt", "torquent", "tortor", "tristique", "turpis", "ullamcorper",
            "ultrices", "ultricies", "urna", "ut", "varius", "vehicula", "vel", "velit",
            "venenatis", "vestibulum", "vitae", "vivamus", "viverra", "volutpat",
            "vulputate"));

    private static List<String> punctuation = new ArrayList<>(Arrays.asList(".", "?"));
    private static String _n = System.getProperty("line.separator");
    private static Random random = new Random();

    private static String random(List<String> list) {
        return list.get(random.nextInt(list.size() - 1));
    }

    public static String randomWord() {
        return random(lipsumwords);
    }

    public static String randomPunctuation() {
        return random(punctuation);
    }

    private static String words(int count) {
        if (count > 0) {
            StringBuilder words = new StringBuilder();
            for (int i = 0; i < count; ++i) {
                if (i > 0) {
                    words.append(" ");
                }
                words.append(randomWord());
            }
            return words.toString();
        }
        return "";
    }

    public static String sentenceFragment() {
        return words(random.nextInt(10) + 3);
    }

    public static String sentence() {
        StringBuilder sentence = new StringBuilder();

        String first = randomWord();

        sentence.append(first.substring(0, 1).toUpperCase());
        sentence.append(first.substring(1));
        sentence.append(" ");

        while (random.nextBoolean()) {
            for (int i = 0; i < random.nextInt(3); ++i) {
                sentence.append(sentenceFragment());
                sentence.append(", ");
            }
        }

        sentence.append(randomPunctuation());

        return sentence.toString();
    }

    public static String sentences(int count) {
        StringBuilder sentences = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            if (i > 0) {
                sentences.append("  ");
            }

            sentences.append(sentence());
        }
        return sentences.toString();
    }

    public static String paragraph(boolean standard) {
        if (standard) {
            return LoremIpsum.standard;
        }

        return sentences(random.nextInt(3) + 2);
    }

    public static String paragraph() {
        return LoremIpsum.standard;
    }

    public static String paragraph(int count) {
        StringBuilder paragraphs = new StringBuilder();

        paragraphs.append(paragraph(true));

        for (int i = 1; i < count; ++i) {
            paragraphs.append(_n);
            paragraphs.append(_n);

            paragraphs.append(paragraph(false));
        }

        return paragraphs.toString();
    }
}
