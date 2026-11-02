package dev.leo.worldcraft.formula;

import dev.leo.worldcraft.config.Issues;
import dev.leo.worldcraft.formula.Lexer.Kind;
import dev.leo.worldcraft.formula.Lexer.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lee una fórmula (plan 034 §1.1): descenso recursivo con la precedencia de WorldEdit. Los nombres se resuelven al leer:
 * las entradas primero, después las variables que se asignan en algún lugar del texto (se buscan antes).
 */
final class FormulaParser {
    static final int MAX_DEPTH = 128;
    static final Map<String, Double> CONSTANTS = Map.of("pi", Math.PI, "e", Math.E, "true", 1.0, "false", 0.0);
    private static final Set<String> KEYWORDS = Set.of("if", "else", "while", "do", "for", "break", "continue", "return");
    private static final Set<String> ASSIGN = Set.of("=", "+=", "-=", "*=", "/=", "%=", "^=", "**=");

    private final List<Token> t;
    private final Map<String, Integer> slots = new LinkedHashMap<>();
    private final Set<String> inputs;
    private int i, depth;

    private FormulaParser(List<Token> tokens, List<String> inputs) {
        this.t = tokens;
        this.inputs = new HashSet<>(inputs);
        for (String in : inputs) slots.put(in, slots.size());
    }

    static Formula parse(String text, List<String> inputs, Formula.Limits limits) throws FormulaException {
        FormulaParser p = new FormulaParser(Lexer.tokens(text), inputs);
        p.declare();
        Node root = p.statements(true);
        return new Formula(text, root, Math.max(3, p.slots.size()), inputs, limits);
    }

    /** Primera pasada: los nombres que se asignan (=, +=, ++, --, rotate, swap) son variables. */
    private void declare() throws FormulaException {
        for (int k = 0; k < t.size(); k++) {
            Token a = t.get(k);
            if (a.kind() != Kind.NAME || KEYWORDS.contains(a.text())) continue;
            Token next = t.get(k + 1), prev = k > 0 ? t.get(k - 1) : null;
            boolean assigned = next.kind() == Kind.OP && (ASSIGN.contains(next.text()) || next.is("++") || next.is("--"))
                    || prev != null && prev.kind() == Kind.OP && (prev.is("++") || prev.is("--"));
            if ((a.is("rotate") || a.is("swap")) && next.is("(")) {
                // rotate(x, y, …) y swap(x, y) cambian sus dos primeras variables.
                if (k + 2 < t.size() && t.get(k + 2).kind() == Kind.NAME) var(t.get(k + 2));
                if (k + 4 < t.size() && t.get(k + 3).is(",") && t.get(k + 4).kind() == Kind.NAME) var(t.get(k + 4));
            }
            if (assigned) var(a);
        }
    }

    private void var(Token name) throws FormulaException {
        String n = name.text();
        if (CONSTANTS.containsKey(n) && !inputs.contains(n)) {
            throw new FormulaException("'" + n + "' at " + name.pos() + " is a constant: it can't be changed");
        }
        slots.putIfAbsent(n, slots.size());
    }

    // ------------------------------------------------------------------ fichas

    private Token peek() {
        return t.get(i);
    }

    private Token peek(int ahead) {
        return t.get(Math.min(t.size() - 1, i + ahead));
    }

    private Token next() {
        return t.get(i++);
    }

    private boolean accept(String op) {
        if (peek().kind() == Kind.OP && peek().is(op)) {
            i++;
            return true;
        }
        return false;
    }

    private boolean acceptWord(String w) {
        if (peek().kind() == Kind.NAME && peek().is(w)) {
            i++;
            return true;
        }
        return false;
    }

    private void expect(String op) throws FormulaException {
        if (!accept(op)) throw new FormulaException("Expected '" + op + "' at " + peek().pos() + found());
    }

    private String found() {
        Token k = peek();
        return k.kind() == Kind.END ? " (the formula ended)" : ", found '" + k.text() + "'";
    }

    private void enter() throws FormulaException {
        if (++depth > MAX_DEPTH) throw new FormulaException("The formula is nested too deeply (more than " + MAX_DEPTH + " levels)");
    }

    // ------------------------------------------------------------------ sentencias

    /** Sentencias separadas por ';' hasta el final (top) o hasta '}'. */
    private Node statements(boolean top) throws FormulaException {
        List<Node> out = new ArrayList<>();
        while (true) {
            while (accept(";")) {
                // ;; vacío
            }
            Token k = peek();
            if (k.kind() == Kind.END) {
                if (!top) throw new FormulaException("Missing '}' at the end of the formula");
                break;
            }
            if (!top && k.is("}")) break;
            if (k.is("}")) throw new FormulaException("Unexpected '}' at " + k.pos());
            boolean block = isBlockStatement(k);
            out.add(statement());
            if (!block) {
                Token n = peek();
                if (!(n.is(";") || n.kind() == Kind.END || (!top && n.is("}")))) {
                    throw new FormulaException("Expected ';' between two parts at " + n.pos() + found());
                }
            }
        }
        if (out.isEmpty()) return new Node.Num(0);
        return out.size() == 1 ? out.get(0) : new Node.Seq(out.toArray(Node[]::new));
    }

    private static boolean isBlockStatement(Token k) {
        return k.kind() == Kind.OP && k.is("{") || k.kind() == Kind.NAME && (k.is("if") || k.is("while") || k.is("for") || k.is("do"));
    }

    private Node statement() throws FormulaException {
        enter();
        try {
            Token k = peek();
            if (k.kind() == Kind.OP && k.is("{")) {
                next();
                Node body = statements(false);
                expect("}");
                return body;
            }
            if (k.kind() == Kind.NAME) {
                switch (k.text()) {
                    case "if" -> {
                        next();
                        expect("(");
                        Node c = expression();
                        expect(")");
                        Node a = statement();
                        if (peek().is(";") && peek(1).kind() == Kind.NAME && peek(1).is("else")) next();
                        Node b = acceptWord("else") ? statement() : null;
                        return new Node.If(c, a, b);
                    }
                    case "while" -> {
                        next();
                        expect("(");
                        Node c = expression();
                        expect(")");
                        return new Node.Loop(null, c, null, statement(), false);
                    }
                    case "do" -> {
                        next();
                        Node body = statement();
                        accept(";");
                        if (!acceptWord("while")) throw new FormulaException("Expected 'while' after 'do' at " + peek().pos() + found());
                        expect("(");
                        Node c = expression();
                        expect(")");
                        return new Node.Loop(null, c, null, body, true);
                    }
                    case "for" -> {
                        return forStatement();
                    }
                    case "break" -> {
                        next();
                        return new Node.Jump(Formula.Run.BREAK, null);
                    }
                    case "continue" -> {
                        next();
                        return new Node.Jump(Formula.Run.CONTINUE, null);
                    }
                    case "return" -> {
                        next();
                        Token n = peek();
                        boolean empty = n.kind() == Kind.END || n.is(";") || n.is("}");
                        return new Node.Jump(Formula.Run.RETURN, empty ? null : expression());
                    }
                    case "else" -> throw new FormulaException("'else' at " + k.pos() + " without an 'if'");
                    case "switch", "case", "default" -> throw new FormulaException("'" + k.text() + "' is not supported: use if and else");
                    default -> {
                    }
                }
            }
            return expression();
        } finally {
            depth--;
        }
    }

    /** for (a; b; c) cuerpo, o for (i = a, b) cuerpo (de a hasta b). */
    private Node forStatement() throws FormulaException {
        next();
        expect("(");
        if (peek().kind() == Kind.NAME && peek(1).is("=")) {
            int save = i;
            Token name = next();
            next();
            Node from = expression();
            if (accept(",")) {
                Node to = expression();
                expect(")");
                return new Node.Range(slots.get(name.text()), from, to, statement());
            }
            i = save;
        }
        Node init = peek().is(";") ? null : expression();
        expect(";");
        Node cond = peek().is(";") ? null : expression();
        expect(";");
        Node inc = peek().is(")") ? null : expression();
        expect(")");
        return new Node.Loop(init, cond, inc, statement(), false);
    }

    // ------------------------------------------------------------------ expresiones

    private Node expression() throws FormulaException {
        enter();
        try {
            return assignment();
        } finally {
            depth--;
        }
    }

    private Node assignment() throws FormulaException {
        Token start = peek();
        Node left = ternary();
        Token op = peek();
        if (op.kind() == Kind.OP && ASSIGN.contains(op.text())) {
            if (!(left instanceof Node.Var v) || start.kind() != Kind.NAME) {
                throw new FormulaException("Only a variable can be changed with '" + op.text() + "' (at " + op.pos() + ")");
            }
            next();
            Node value = assignment();
            char c = op.text().equals("=") ? 0 : op.text().equals("**=") ? '^' : op.text().charAt(0);
            return new Node.Assign(v.slot, c, value);
        }
        return left;
    }

    private Node ternary() throws FormulaException {
        Node c = or();
        if (accept("?")) {
            Node a = assignment();
            expect(":");
            Node b = assignment();
            return new Node.Cond(c, a, b);
        }
        return c;
    }

    private Node or() throws FormulaException {
        Node a = and();
        while (accept("||")) a = new Node.Or(a, and());
        return a;
    }

    private Node and() throws FormulaException {
        Node a = equality();
        while (accept("&&")) a = new Node.And(a, equality());
        return a;
    }

    private Node equality() throws FormulaException {
        Node a = relational();
        while (true) {
            if (accept("==")) a = new Node.Binary(Node.Binary.EQ, a, relational());
            else if (accept("!=")) a = new Node.Binary(Node.Binary.NE, a, relational());
            else if (accept("~=")) a = new Node.Binary(Node.Binary.NEAR, a, relational());
            else return a;
        }
    }

    private Node relational() throws FormulaException {
        Node a = shift();
        while (true) {
            if (accept("<=")) a = new Node.Binary(Node.Binary.LE, a, shift());
            else if (accept(">=")) a = new Node.Binary(Node.Binary.GE, a, shift());
            else if (accept("<")) a = new Node.Binary(Node.Binary.LT, a, shift());
            else if (accept(">")) a = new Node.Binary(Node.Binary.GT, a, shift());
            else return a;
        }
    }

    private Node shift() throws FormulaException {
        Node a = additive();
        while (true) {
            if (accept("<<")) a = new Node.Binary(Node.Binary.SHL, a, additive());
            else if (accept(">>")) a = new Node.Binary(Node.Binary.SHR, a, additive());
            else return a;
        }
    }

    private Node additive() throws FormulaException {
        Node a = multiplicative();
        while (true) {
            if (accept("+")) a = new Node.Binary(Node.Binary.ADD, a, multiplicative());
            else if (accept("-")) a = new Node.Binary(Node.Binary.SUB, a, multiplicative());
            else return a;
        }
    }

    private Node multiplicative() throws FormulaException {
        Node a = power();
        while (true) {
            if (accept("*")) a = new Node.Binary(Node.Binary.MUL, a, power());
            else if (accept("/")) a = new Node.Binary(Node.Binary.DIV, a, power());
            else if (accept("%")) a = new Node.Binary(Node.Binary.MOD, a, power());
            else return a;
        }
    }

    /** La potencia va de derecha a izquierda y debajo de los signos: -2^2 = 4, como WorldEdit. */
    private Node power() throws FormulaException {
        Node a = unary();
        if (accept("^") || accept("**")) {
            enter();
            try {
                return new Node.Binary(Node.Binary.POW, a, power());
            } finally {
                depth--;
            }
        }
        return a;
    }

    private Node unary() throws FormulaException {
        Token k = peek();
        if (k.kind() == Kind.OP) {
            switch (k.text()) {
                case "-", "+", "!", "~" -> {
                    next();
                    enter();
                    try {
                        return new Node.Unary(k.text().charAt(0), unary());
                    } finally {
                        depth--;
                    }
                }
                case "++", "--" -> {
                    next();
                    Token name = next();
                    if (name.kind() != Kind.NAME || !slots.containsKey(name.text())) {
                        throw new FormulaException("'" + k.text() + "' at " + k.pos() + " needs a variable after it");
                    }
                    return new Node.Step(slots.get(name.text()), k.is("++") ? 1 : -1, true);
                }
                default -> {
                }
            }
        }
        return postfix();
    }

    private Node postfix() throws FormulaException {
        Token start = peek();
        Node a = primary();
        while (true) {
            Token k = peek();
            if (k.kind() == Kind.OP && (k.is("++") || k.is("--"))) {
                if (!(a instanceof Node.Var v) || start.kind() != Kind.NAME) {
                    throw new FormulaException("'" + k.text() + "' at " + k.pos() + " needs a variable before it");
                }
                next();
                a = new Node.Step(v.slot, k.is("++") ? 1 : -1, false);
            } else if (k.kind() == Kind.OP && k.is("!") && !startsOperand(peek(1))) {
                next();
                a = new Node.Factorial(a);
            } else {
                return a;
            }
        }
    }

    /** Lo que viene después de un «!» que es factorial no puede empezar otro valor (si no, «!» sería un error). */
    private static boolean startsOperand(Token k) {
        return k.kind() == Kind.NUM || k.kind() == Kind.NAME || k.kind() == Kind.OP && k.is("(");
    }

    private Node primary() throws FormulaException {
        Token k = next();
        switch (k.kind()) {
            case NUM -> {
                return new Node.Num(k.value());
            }
            case NAME -> {
                String n = k.text();
                if (KEYWORDS.contains(n)) throw new FormulaException("Unexpected '" + n + "' at " + k.pos());
                if (peek().kind() == Kind.OP && peek().is("(")) return call(k);
                Integer slot = slots.get(n);
                if (slot != null) return new Node.Var(slot);
                Double c = CONSTANTS.get(n);
                if (c != null) return new Node.Num(c);
                List<String> known = new ArrayList<>(slots.keySet());
                known.addAll(CONSTANTS.keySet());
                String lower = n.toLowerCase(Locale.ROOT);
                if (!lower.equals(n) && (slots.containsKey(lower) || CONSTANTS.containsKey(lower))) {
                    throw new FormulaException("Unknown variable '" + n + "' at " + k.pos() + " (names are lowercase: '" + lower + "')");
                }
                throw new FormulaException("Unknown variable '" + n + "' at " + k.pos() + Issues.hint(n, known)
                        + (inputs.isEmpty() ? "" : ". You can use " + String.join(", ", inputs.stream().sorted().toList()) + " and your own (a = 1)"));
            }
            case OP -> {
                if (k.is("(")) {
                    enter();
                    try {
                        Node e = expression();
                        expect(")");
                        return e;
                    } finally {
                        depth--;
                    }
                }
                if (k.is(")")) throw new FormulaException("Unexpected ')' at " + k.pos() + " (one too many?)");
                throw new FormulaException("Unexpected '" + k.text() + "' at " + k.pos());
            }
            default -> throw new FormulaException("The formula ends too soon: something is missing at " + k.pos());
        }
    }

    private Node call(Token name) throws FormulaException {
        String n = name.text().toLowerCase(Locale.ROOT);
        expect("(");
        List<Node> args = new ArrayList<>();
        List<Token> starts = new ArrayList<>();
        if (!accept(")")) {
            do {
                starts.add(peek());
                args.add(expression());
            } while (accept(","));
            expect(")");
        }
        if (n.equals("rotate") || n.equals("swap")) {
            int want = n.equals("rotate") ? 3 : 2;
            if (args.size() != want) throw new FormulaException(n + " at " + name.pos() + " takes " + want + " values, not " + args.size());
            if (!(args.get(0) instanceof Node.Var a) || !(args.get(1) instanceof Node.Var b) || starts.get(0).kind() != Kind.NAME
                    || starts.get(1).kind() != Kind.NAME) {
                throw new FormulaException(n + " at " + name.pos() + " changes two variables: write their names, like " + n
                        + (want == 3 ? "(x, z, pi/4)" : "(x, z)"));
            }
            return new Node.Pair(a.slot, b.slot, want == 3 ? args.get(2) : null);
        }
        Functions.Fn fn = Functions.find(n);
        if (fn == null) {
            String why = Functions.missing(n);
            if (why != null) throw new FormulaException(why);
            throw new FormulaException("Unknown function '" + name.text() + "' at " + name.pos() + Issues.hint(n, Functions.names()));
        }
        if (args.size() < fn.min || args.size() > fn.max) {
            String want = fn.min == fn.max ? String.valueOf(fn.min) : fn.max >= 64 ? fn.min + " or more" : fn.min + " or " + fn.max;
            throw new FormulaException(n + " at " + name.pos() + " takes " + want + " value" + (want.equals("1") ? "" : "s") + ", not " + args.size());
        }
        return new Node.Call(fn, args.toArray(Node[]::new));
    }
}
