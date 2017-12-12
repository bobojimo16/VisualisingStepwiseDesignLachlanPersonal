package mc.client.ui;

import mc.plugins.PluginManager;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyntaxHighlighting {


    //initialise the syntax from plugins
    static {
        functions = PluginManager.getInstance().getFunctionList();
    }

    static final String[] processTypes = new String[] {
            "automata", "petrinet", "operation", "equation",

    };

    static final String[] functions;

    static final String[] terminals = new String[] {
            "STOP", "ERROR"
    };

    static final String[] keywords = new String[] {
            "const", "range", "set", "if", "then", "else", "when", "forall"
    };

    static final String PROCESSTYPES_PATTERN = "\\b(" + String.join("|", processTypes) + ")\\b";
    static final String FUNCTIONS_PATTERN = "\\b(" + String.join("|", functions) + ")\\b";
    static final String TERMINALS_PATTERN = "\\b(" + String.join("|", terminals) + ")\\b";
    static final String KEYWORDS_PATTERN = "\\b(" + String.join("|", keywords) + ")\\b";

    static final String SYMBOLS = "\\.\\.|\\.|,|:|\\[|\\]|\\(|\\)|->|~>|\\\\|@|\\$|\\?";
    static final String OPERATORS = "\\|\\||\\||&&|&|\\^|==|=|!=|<<|<=|<|>>|>=|>|\\+|-|\\*|\\/|%|!|\\?";
    static final String OPERATIONS = "~|#";
    static final String ACTION_LABEL_PATTERN = "[a-z][A-Za-z0-9_]*";
    static final String IDENT_PATTERN = "[A-Z][A-Za-z0-9_]*\\*?";
    static final String INT_PATTERN = "[0-9]+";

    static final String PAREN_PATTERN = "\\(|\\)";
    static final String BRACE_PATTERN = "\\{|\\}";
    static final String BRACKET_PATTERN = "\\[|\\]";
    static final String COMMENT_PATTERN = "(?://[^\n]*)|(?:/\\*.*?\\*/)|(?:/\\*(?!\\*/).*)";

    static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>" + COMMENT_PATTERN + ")"
                    + "|(?<PROCESSTYPE>" + PROCESSTYPES_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTIONS_PATTERN + ")"
                    + "|(?<TERMINAL>" + TERMINALS_PATTERN + ")"
                    + "|(?<KEYWORD>" + KEYWORDS_PATTERN + ")"
                    + "|(?<SYMBOL>" + SYMBOLS + ")"
                    + "|(?<OPERATOR>" + OPERATORS + ")"
                    + "|(?<OPERATION>" + OPERATIONS + ")"
                    + "|(?<ACTIONLABEL>" + ACTION_LABEL_PATTERN + ")"
                    + "|(?<IDENTIFER>" + IDENT_PATTERN + ")"
                    + "|(?<INT>" + INT_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
            , Pattern.DOTALL
    );

    static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = SyntaxHighlighting.PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();


        while(matcher.find()) {
            String styleClass;

            if (matcher.group("PROCESSTYPE") != null) {
                styleClass = "process";
            } else if (matcher.group("FUNCTION") != null) {
                styleClass = "function";
            } else if (matcher.group("TERMINAL") != null) {
                if (matcher.group("TERMINAL").equals("STOP"))
                    styleClass = "terminalStop";
                else
                    styleClass = "terminalError";
            } else if(matcher.group("KEYWORD") != null) {
                styleClass = "keyword";
            }else if(matcher.group("SYMBOL") != null) {
                styleClass = "symbol";
            } else if(matcher.group("OPERATOR") != null) {
                styleClass = "operator";
            } else if(matcher.group("OPERATION") != null) {
                styleClass = "operation";
            } else if(matcher.group("ACTIONLABEL") != null) {
                styleClass = "actionLabel";
            } else if(matcher.group("IDENTIFER") != null) {
                styleClass = "identifier";
            } else if(matcher.group("INT") != null) {
                styleClass = "number";
            } else if(matcher.group("PAREN") != null) {
                styleClass = "paren";

            } else if(matcher.group("BRACE") != null) {
                styleClass = "brace";

            }else if(matcher.group("BRACKET") != null) {
                styleClass = "bracket";

            } else if(matcher.group("COMMENT") != null) {
                styleClass = "comment";
            } else {
                styleClass = null;
            }


            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
