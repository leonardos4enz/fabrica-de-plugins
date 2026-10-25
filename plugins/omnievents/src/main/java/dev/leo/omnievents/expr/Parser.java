package dev.leo.omnievents.expr;

import dev.leo.omnievents.util.Suggest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Analizador de precedencia (Pratt). Ver la tabla de precedencia en el plan 001 §2.3. */
public final class Parser {
    private static final int TERNARY = 1, OR = 2, AND = 3, EQUALITY = 4, RELATION = 5, ADD = 6, MUL = 7, UNARY = 8;

    private final List<Token> tokens;
    private int p;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Node parse(String src) {
        if (src == null || src.isBlank()) throw new ExprException("La expresión está vacía", 1);
        Parser parser = new Parser(Lexer.tokenize(src));
        Node n = parser.expr(0);
        Token t = parser.peek();
        if (t.type() != Token.Type.EOF) {
            throw new ExprException("Sobra '" + t.text() + "' (¿falta un operador como && o ||?)", t.pos());
        }
        return n;
    }

    private Token peek() {
        return tokens.get(p);
    }

    private Token peek(int ahead) {
        return tokens.get(Math.min(p + ahead, tokens.size() - 1));
    }

    private Token next() {
        return tokens.get(p++);
    }

    private Token expect(Token.Type type, String what) {
        Token t = next();
        if (t.type() != type) {
            throw new ExprException("Se esperaba " + what + (t.type() == Token.Type.EOF ? " al final" : " y hay '" + t.text() + "'"), t.pos());
        }
        return t;
    }

    private Node expr(int minPrec) {
        Node left = prefix();
        while (true) {
            Token t = peek();
            if (t.type() == Token.Type.QUESTION) {
                if (TERNARY < minPrec) break;
                next();
                Node a = expr(0);
                expect(Token.Type.COLON, "':' del ternario");
                Node b = expr(TERNARY);
                left = new Node.Ternary(left, a, b);
                continue;
            }
            Infix in = infix();
            if (in == null || in.prec < minPrec) break;
            p += in.width;
            Node right = expr(in.prec + 1);
            left = switch (in.kind) {
                case "or" -> new Node.Or(left, right);
                case "and" -> new Node.And(left, right);
                default -> new Node.Binary(in.op, in.negated, left, right);
            };
        }
        return left;
    }

    private record Infix(String kind, Node.Op op, boolean negated, int prec, int width) {
    }

    /** Operador infijo en la posición actual, sin consumirlo. */
    private Infix infix() {
        Token t = peek();
        if (t.type() == Token.Type.OP) {
            switch (t.text()) {
                case "||": return new Infix("or", null, false, OR, 1);
                case "&&": return new Infix("and", null, false, AND, 1);
                case "==": return bin(Node.Op.EQ, false, EQUALITY, 1);
                case "!=": return bin(Node.Op.NE, false, EQUALITY, 1);
                case "~=": return bin(Node.Op.IEQ, false, EQUALITY, 1);
                case "!~=": return bin(Node.Op.INE, false, EQUALITY, 1);
                case "<": return bin(Node.Op.LT, false, RELATION, 1);
                case "<=": return bin(Node.Op.LE, false, RELATION, 1);
                case ">": return bin(Node.Op.GT, false, RELATION, 1);
                case ">=": return bin(Node.Op.GE, false, RELATION, 1);
                case "+": return bin(Node.Op.ADD, false, ADD, 1);
                case "-": return bin(Node.Op.SUB, false, ADD, 1);
                case "*": return bin(Node.Op.MUL, false, MUL, 1);
                case "/": return bin(Node.Op.DIV, false, MUL, 1);
                case "%": return bin(Node.Op.MOD, false, MUL, 1);
                case "!": {
                    Token w = peek(1);
                    if (w.type() == Token.Type.IDENT) {
                        Node.Op op = Node.Op.byWord(w.text());
                        if (op != null) return bin(op, true, op == Node.Op.EQ || op == Node.Op.IEQ ? EQUALITY : RELATION, 2);
                    }
                    return null;
                }
                default: return null;
            }
        }
        if (t.type() == Token.Type.IDENT) {
            String w = t.text().toLowerCase(Locale.ROOT);
            if (w.equals("or")) return new Infix("or", null, false, OR, 1);
            if (w.equals("and")) return new Infix("and", null, false, AND, 1);
            if (w.equals("not") && peek(1).is(Token.Type.IDENT, "in")) return bin(Node.Op.IN, true, RELATION, 2);
            Node.Op op = Node.Op.byWord(w);
            if (op != null) return bin(op, false, op == Node.Op.EQ || op == Node.Op.IEQ ? EQUALITY : RELATION, 1);
        }
        return null;
    }

    private static Infix bin(Node.Op op, boolean neg, int prec, int width) {
        return new Infix("bin", op, neg, prec, width);
    }

    private Node prefix() {
        Token t = next();
        Node n = switch (t.type()) {
            case NUMBER -> new Node.Literal(t.value());
            case STRING -> new Node.Literal(t.value());
            case PLACEHOLDER -> new Node.Placeholder(t.text());
            case LPAREN -> {
                Node inner = expr(0);
                expect(Token.Type.RPAREN, "')'");
                yield inner;
            }
            case LBRACK -> new Node.ListNode(args(Token.Type.RBRACK, "']'"));
            case OP -> switch (t.text()) {
                case "!" -> new Node.Not(expr(UNARY));
                case "-" -> negate(expr(UNARY));
                case "+" -> expr(UNARY);
                default -> throw new ExprException("Falta un valor antes de '" + t.text() + "'", t.pos());
            };
            case IDENT -> ident(t);
            case EOF -> throw new ExprException("La expresión termina antes de tiempo", t.pos());
            default -> throw new ExprException("No se esperaba '" + t.text() + "'", t.pos());
        };
        return postfix(n);
    }

    private static Node negate(Node n) {
        if (n instanceof Node.Literal l && l.value() instanceof Double d) return new Node.Literal(-d);
        return new Node.Neg(n);
    }

    private Node ident(Token t) {
        String name = t.text();
        String lower = name.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "true": return new Node.Literal(Boolean.TRUE);
            case "false": return new Node.Literal(Boolean.FALSE);
            case "null": return new Node.Literal(null);
            case "not": return new Node.Not(expr(UNARY));
            default: break;
        }
        Scope scope = Scope.byPrefix(name);
        if (scope != null && peek().type() == Token.Type.DOT && peek(1).type() == Token.Type.IDENT) {
            next();
            return new Node.VarRef(scope, next().text());
        }
        if (peek().type() == Token.Type.LPAREN) {
            next();
            List<Node> a = args(Token.Type.RPAREN, "')'");
            Functions.Def def = Functions.find(lower);
            if (def == null) {
                String s = Suggest.closest(lower, Functions.names());
                throw new ExprException("Función desconocida '" + name + "'" + (s != null ? " (¿quisiste decir '" + s + "'?)" : ""), t.pos());
            }
            if (a.size() < def.min() || a.size() > def.max()) {
                String n = def.min() == def.max() ? String.valueOf(def.min()) : def.min() + "–" + (def.max() == Integer.MAX_VALUE ? "∞" : def.max());
                throw new ExprException("'" + def.name() + "' recibe " + n + " argumentos y le diste " + a.size(), t.pos());
            }
            if (def.name().equals("ttl")) checkTtl(a.get(0), t);
            return new Node.Call(def.name(), def, a);
        }
        return new Node.Ident(name);
    }

    /** ttl() recibe una variable que puede caducar, no un valor (spec 009, H1 CA4). */
    private static void checkTtl(Node arg, Token t) {
        if (arg instanceof Node.DataRef) return;
        if (arg instanceof Node.VarRef v && v.scope() != Scope.LOCAL) return;
        throw new ExprException("'ttl' recibe una variable que puede caducar: pvar.nombre, gvar.nombre u objeto.data.nombre (y le diste " + arg.text() + ")", t.pos());
    }

    private List<Node> args(Token.Type close, String closeText) {
        List<Node> out = new ArrayList<>();
        if (peek().type() == close) {
            next();
            return out;
        }
        while (true) {
            out.add(expr(0));
            Token t = next();
            if (t.type() == close) return out;
            if (t.type() != Token.Type.COMMA) {
                throw new ExprException("Se esperaba ',' o " + closeText + (t.type() == Token.Type.EOF ? " al final" : " y hay '" + t.text() + "'"), t.pos());
            }
        }
    }

    private Node postfix(Node n) {
        while (true) {
            Token t = peek();
            if (t.type() == Token.Type.DOT) {
                next();
                Token name = expect(Token.Type.IDENT, "un nombre de propiedad después de '.'");
                if (name.text().equals("data") && peek().type() == Token.Type.DOT && peek(1).type() == Token.Type.IDENT
                        && peek(2).type() != Token.Type.LPAREN) {
                    // Spec 009: X.data.nombre es una variable del objeto (bloque, entidad, ítem).
                    next();
                    n = new Node.DataRef(n, next().text());
                    continue;
                }
                if (peek().type() == Token.Type.LPAREN) {
                    next();
                    n = new Node.MethodCall(n, name.text(), args(Token.Type.RPAREN, "')'"));
                } else {
                    n = new Node.Prop(n, name.text());
                }
            } else if (t.type() == Token.Type.LBRACK) {
                next();
                Node idx = expr(0);
                expect(Token.Type.RBRACK, "']'");
                n = new Node.Index(n, idx);
            } else {
                return n;
            }
        }
    }
}
