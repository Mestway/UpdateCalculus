package polyglot.ext.jl5;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.List;

import polyglot.ast.NodeFactory;
import polyglot.ext.jl5.ast.JL5NodeFactory_c;
import polyglot.ext.jl5.parse.Grm;
import polyglot.ext.jl5.parse.Lexer_c;
import polyglot.ext.jl5.types.JL5TypeSystem_c;
import polyglot.ext.jl5.types.reflect.JL5ClassFile;
import polyglot.ext.jl5.types.reflect.JL5Method;
import polyglot.ext.jl5.visit.ApplicationChecker;
import polyglot.ext.jl5.visit.JL5AmbiguityRemover;
import polyglot.frontend.BarrierPass;
import polyglot.frontend.CupParser;
import polyglot.frontend.FileSource;
import polyglot.frontend.GlobalBarrierPass;
import polyglot.frontend.Job;
import polyglot.frontend.Parser;
import polyglot.frontend.Pass;
import polyglot.frontend.VisitorPass;
import polyglot.lex.Lexer;
import polyglot.types.TypeSystem;
import polyglot.types.reflect.ClassFile;
import polyglot.types.reflect.Method;
import polyglot.util.ErrorQueue;

/**
 * Extension information for jl5 extension.
 */
public class ExtensionInfo extends polyglot.ext.jl.ExtensionInfo {
    static {
        // force Topics to load
        Topics t = new Topics();
    }

    public static final polyglot.frontend.Pass.ID ENUM_SWITCH_DISAMBIGUATE = new polyglot.frontend.Pass.ID("enum-switch-disambiguate");
    public static final polyglot.frontend.Pass.ID TYPE_CHECK_ALL = new polyglot.frontend.Pass.ID("type-check-all");
    public static final polyglot.frontend.Pass.ID APPLICATION_CHECK = new polyglot.frontend.Pass.ID("application-check");
    public static final polyglot.frontend.Pass.ID GENERIC_TYPE_HANDLER = new polyglot.frontend.Pass.ID("generic-type-handler");

    public static final polyglot.frontend.Pass.ID TYPE_VARS_ALL = new polyglot.frontend.Pass.ID("type-vars-all");

    public static final polyglot.frontend.Pass.ID ADD_TYPE_VARS = new polyglot.frontend.Pass.ID("add-type-vars");

    public static final polyglot.frontend.Pass.ID SIMPLIFY = new polyglot.frontend.Pass.ID("simplify");

    public static final polyglot.frontend.Pass.ID BOXING = new polyglot.frontend.Pass.ID("boxing");

    public static final polyglot.frontend.Pass.ID UNBOXING = new polyglot.frontend.Pass.ID("unboxing");

    public static final polyglot.frontend.Pass.ID LET_INSERTER = new polyglot.frontend.Pass.ID("let-inserter");

    public static final polyglot.frontend.Pass.ID GENERIC_CAST_INSERTER = new polyglot.frontend.Pass.ID("generic-cast-inserter");

    //public static final polyglot.frontend.Pass.ID GENERIC_ARGS = new polyglot.frontend.Pass.ID("generic-args");
    //public static final polyglot.frontend.Pass.ID GENERIC_RESET = new polyglot.frontend.Pass.ID("generic-reset");

    public String defaultFileExtension() {
        return "jl5";
    }

    public String compilerName() {
        return "jl5c";
    }

    public Parser parser(Reader reader, FileSource source, ErrorQueue eq) {
        Lexer lexer = new Lexer_c(reader, source.name(), eq);
        Grm grm = new Grm(lexer, ts, nf, eq);
        return new CupParser(grm, source, eq);
    }

    protected NodeFactory createNodeFactory() {
        return new JL5NodeFactory_c();
    }

    protected TypeSystem createTypeSystem() {
        return new JL5TypeSystem_c();
    }

    public List passes(Job job) {
        getOptions().serialize_type_info = false;
        List passes = super.passes(job);
        afterPass(passes, Pass.ADD_MEMBERS_ALL, new VisitorPass(GENERIC_TYPE_HANDLER, job, new JL5AmbiguityRemover(job, ts, nf, JL5AmbiguityRemover.TYPE_VARS)));
        afterPass(passes, GENERIC_TYPE_HANDLER, new GlobalBarrierPass(TYPE_VARS_ALL, job));
        afterPass(passes, Pass.TYPE_CHECK, new BarrierPass(TYPE_CHECK_ALL, job));
        beforePass(passes, Pass.REACH_CHECK, new VisitorPass(APPLICATION_CHECK, job, new ApplicationChecker(job, ts, nf)));


        return passes;
    }

    public ClassFile createClassFile(File classFileSource, byte[] code) {
        return new JL5ClassFile(classFileSource, code, this);
    }

    public Method createMethod(DataInputStream in, ClassFile clazz) throws IOException {
        Method m = new JL5Method(in, clazz);
        m.initialize();
        return m;
    }

}
