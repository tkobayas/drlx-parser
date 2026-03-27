package org.drools.drlx.builder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ListTokenSource;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.drools.drlx.builder.proto.DrlxParseTreeProto;
import org.drools.drlx.parser.DrlxParser;

/**
 * Persists an ANTLR parse tree snapshot as protobuf and rehydrates it back into
 * generated {@link DrlxParser} context objects for the runtime build path.
 */
public final class DrlxParseTreeSnapshot {

    public static final String ENABLED_PROPERTY = "drlx.compiler.serializedParseTree";

    private static final String FILE_NAME = "drlx-parse-tree.pb";

    private DrlxParseTreeSnapshot() {
    }

    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "false"));
    }

    public static Path snapshotFilePath(Path dir) {
        return dir.resolve(FILE_NAME);
    }

    public static void save(String drlxSource,
                            DrlxParser.DrlxCompilationUnitContext ctx,
                            CommonTokenStream tokenStream,
                            Path outputDir) throws IOException {
        tokenStream.fill();

        DrlxParseTreeProto.ParseTreeSnapshot snapshot = DrlxParseTreeProto.ParseTreeSnapshot.newBuilder()
                .setSourceHash(hashSource(drlxSource))
                .addAllTokens(toProtoTokens(tokenStream.getTokens()))
                .setRoot(toProtoNode(ctx))
                .build();

        Files.createDirectories(outputDir);
        try (OutputStream out = Files.newOutputStream(snapshotFilePath(outputDir))) {
            snapshot.writeTo(out);
        }
    }

    public static Rehydrated load(String drlxSource, Path snapshotFile) throws IOException {
        if (!Files.exists(snapshotFile)) {
            return null;
        }

        DrlxParseTreeProto.ParseTreeSnapshot snapshot;
        try (InputStream in = Files.newInputStream(snapshotFile)) {
            snapshot = DrlxParseTreeProto.ParseTreeSnapshot.parseFrom(in);
        }

        if (!snapshot.getSourceHash().equals(hashSource(drlxSource))) {
            return null;
        }

        List<Token> tokens = new ArrayList<>(snapshot.getTokensCount());
        for (DrlxParseTreeProto.TokenSnapshot tokenSnapshot : snapshot.getTokensList()) {
            tokens.add(fromProtoToken(tokenSnapshot));
        }

        CommonTokenStream tokenStream = new CommonTokenStream(new ListTokenSource(tokens));
        tokenStream.fill();

        ParseTree root = fromProtoNode(snapshot.getRoot(), null, tokenStream.getTokens());
        return new Rehydrated((DrlxParser.DrlxCompilationUnitContext) root, tokenStream);
    }

    public record Rehydrated(DrlxParser.DrlxCompilationUnitContext context, CommonTokenStream tokens) {
    }

    private static String hashSource(String drlxSource) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(drlxSource.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static List<DrlxParseTreeProto.TokenSnapshot> toProtoTokens(List<Token> tokens) {
        List<DrlxParseTreeProto.TokenSnapshot> snapshots = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            DrlxParseTreeProto.TokenSnapshot.Builder builder = DrlxParseTreeProto.TokenSnapshot.newBuilder()
                    .setType(token.getType())
                    .setLine(token.getLine())
                    .setCharPositionInLine(token.getCharPositionInLine())
                    .setChannel(token.getChannel())
                    .setStartIndex(token.getStartIndex())
                    .setStopIndex(token.getStopIndex())
                    .setTokenIndex(token.getTokenIndex());
            String text = token.getText();
            if (text != null) {
                builder.setText(text);
            }
            snapshots.add(builder.build());
        }
        return snapshots;
    }

    private static DrlxParseTreeProto.NodeSnapshot toProtoNode(ParseTree tree) {
        if (tree instanceof ParserRuleContext ctx) {
            DrlxParseTreeProto.NodeSnapshot.Builder builder = DrlxParseTreeProto.NodeSnapshot.newBuilder()
                    .setKind(DrlxParseTreeProto.NodeSnapshot.Kind.RULE_CONTEXT)
                    .setClassName(ctx.getClass().getName())
                    .setInvokingState(ctx.invokingState)
                    .setStartTokenIndex(tokenIndex(ctx.getStart()))
                    .setStopTokenIndex(tokenIndex(ctx.getStop()));
            for (int i = 0; i < ctx.getChildCount(); i++) {
                builder.addChildren(toProtoNode(ctx.getChild(i)));
            }
            return builder.build();
        }

        if (tree instanceof TerminalNode node) {
            return DrlxParseTreeProto.NodeSnapshot.newBuilder()
                    .setKind(DrlxParseTreeProto.NodeSnapshot.Kind.TERMINAL)
                    .setTokenIndex(tokenIndex(node.getSymbol()))
                    .build();
        }

        throw new IllegalArgumentException("Unsupported parse tree node type: " + tree.getClass().getName());
    }

    private static ParseTree fromProtoNode(DrlxParseTreeProto.NodeSnapshot snapshot,
                                           ParserRuleContext parent,
                                           List<Token> tokens) {
        if (snapshot.getKind() == DrlxParseTreeProto.NodeSnapshot.Kind.TERMINAL) {
            TerminalNodeImpl node = new TerminalNodeImpl(tokenAt(tokens, snapshot.getTokenIndex()));
            node.setParent(parent);
            parent.addChild(node);
            return node;
        }

        ParserRuleContext ctx = newContext(snapshot.getClassName(), parent, snapshot.getInvokingState());
        ctx.start = tokenAt(tokens, snapshot.getStartTokenIndex());
        ctx.stop = tokenAt(tokens, snapshot.getStopTokenIndex());
        if (parent != null) {
            parent.addChild(ctx);
        }
        for (DrlxParseTreeProto.NodeSnapshot child : snapshot.getChildrenList()) {
            fromProtoNode(child, ctx, tokens);
        }
        return ctx;
    }

    private static ParserRuleContext newContext(String className, ParserRuleContext parent, int invokingState) {
        try {
            Class<? extends ParserRuleContext> contextClass = Class.forName(className).asSubclass(ParserRuleContext.class);
            return instantiateContext(contextClass, parent, invokingState);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to rehydrate parse tree context " + className, e);
        }
    }

    private static ParserRuleContext instantiateContext(Class<? extends ParserRuleContext> contextClass,
                                                        ParserRuleContext parent,
                                                        int invokingState) throws ReflectiveOperationException {
        try {
            Constructor<? extends ParserRuleContext> constructor =
                    contextClass.getConstructor(ParserRuleContext.class, int.class);
            return constructor.newInstance(parent, invokingState);
        } catch (NoSuchMethodException ignored) {
            for (Constructor<?> constructor : contextClass.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && ParserRuleContext.class.isAssignableFrom(parameterTypes[0])) {
                    @SuppressWarnings("unchecked")
                    Class<? extends ParserRuleContext> baseContextClass =
                            (Class<? extends ParserRuleContext>) parameterTypes[0];
                    ParserRuleContext baseContext = instantiateContext(baseContextClass, parent, invokingState);
                    return (ParserRuleContext) constructor.newInstance(baseContext);
                }
            }
            throw new NoSuchMethodException("No supported constructor found for " + contextClass.getName());
        }
    }

    private static CommonToken fromProtoToken(DrlxParseTreeProto.TokenSnapshot snapshot) {
        CommonToken token = new CommonToken(snapshot.getType(), snapshot.getText());
        token.setLine(snapshot.getLine());
        token.setCharPositionInLine(snapshot.getCharPositionInLine());
        token.setChannel(snapshot.getChannel());
        token.setStartIndex(snapshot.getStartIndex());
        token.setStopIndex(snapshot.getStopIndex());
        token.setTokenIndex(snapshot.getTokenIndex());
        return token;
    }

    private static int tokenIndex(Token token) {
        return token == null ? -1 : token.getTokenIndex();
    }

    private static Token tokenAt(List<Token> tokens, int tokenIndex) {
        if (tokenIndex < 0 || tokenIndex >= tokens.size()) {
            return null;
        }
        return tokens.get(tokenIndex);
    }
}
