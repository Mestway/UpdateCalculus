package polyglot.ext.jl5.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import polyglot.ast.Block;
import polyglot.ast.Formal;
import polyglot.ast.Node;
import polyglot.ast.TypeNode;
import polyglot.ext.jl.ast.MethodDecl_c;
import polyglot.ext.jl5.types.FlagAnnotations;
import polyglot.ext.jl5.types.JL5Context;
import polyglot.ext.jl5.types.JL5Flags;
import polyglot.ext.jl5.types.JL5MethodInstance;
import polyglot.ext.jl5.types.JL5TypeSystem;
import polyglot.ext.jl5.types.ParameterizedType;
import polyglot.ext.jl5.types.TypeVariable;
import polyglot.ext.jl5.visit.ApplicationCheck;
import polyglot.ext.jl5.visit.ApplicationChecker;
import polyglot.ext.jl5.visit.JL5AmbiguityRemover;
import polyglot.types.Context;
import polyglot.types.Flags;
import polyglot.types.ParsedClassType;
import polyglot.types.SemanticException;
import polyglot.types.Type;
import polyglot.types.TypeSystem;
import polyglot.util.CodeWriter;
import polyglot.util.CollectionUtil;
import polyglot.util.Position;
import polyglot.util.TypedList;
import polyglot.visit.AmbiguityRemover;
import polyglot.visit.NodeVisitor;
import polyglot.visit.PrettyPrinter;
import polyglot.visit.Translator;
import polyglot.visit.TypeBuilder;
import polyglot.visit.TypeChecker;

public class JL5MethodDecl_c extends MethodDecl_c implements JL5MethodDecl, ApplicationCheck {

    protected boolean compilerGenerated;
    protected List annotations;
    protected List runtimeAnnotations;
    protected List classAnnotations;
    protected List sourceAnnotations;
    protected List<ParamTypeNode> paramTypes;
    
    public JL5MethodDecl_c(Position pos, FlagAnnotations flags, TypeNode returnType, String name, List formals, List throwTypes, Block body){
        super(pos, flags.classicFlags(), returnType, name, formals, throwTypes, body);
        if (flags.annotations() != null){
            this.annotations = flags.annotations();
        }
        else {
            this.annotations = new TypedList(new LinkedList(), AnnotationElem.class, true);
        }
       
        this.paramTypes = new ArrayList<ParamTypeNode>();
    }
    
    public JL5MethodDecl_c(Position pos, FlagAnnotations flags, TypeNode returnType, String name, List formals, List throwTypes, Block body, List paramTypes){
        super(pos, flags.classicFlags(), returnType, name, formals, throwTypes, body);
        if (flags.annotations() != null){
            this.annotations = flags.annotations();
        }
        else {
            this.annotations = new TypedList(new LinkedList(), AnnotationElem.class, true);
        }
        this.paramTypes = paramTypes;
    }
    
    
    public boolean isGeneric(){
        if (!paramTypes.isEmpty()) return true;
        return false;
    }
    
    public boolean isCompilerGenerated(){
        return compilerGenerated;
    }

    public JL5MethodDecl setCompilerGenerated(boolean val){
        JL5MethodDecl_c n = (JL5MethodDecl_c) copy();
        n.compilerGenerated = val;
        return n;
    }

    public List annotations(){
        return this.annotations;
    }

    public JL5MethodDecl annotations(List annotations){
        JL5MethodDecl_c n = (JL5MethodDecl_c) copy();
        n.annotations = annotations;
        return n;    
    }
    
    public List paramTypes(){
        return this.paramTypes;
    }

    public JL5MethodDecl paramTypes(List paramTypes){
        JL5MethodDecl_c n = (JL5MethodDecl_c) copy();
        n.paramTypes = paramTypes;
        return n;
    }
           
    protected MethodDecl_c reconstruct(TypeNode returnType, List formals, List throwTypes, Block body, List annotations, List paramTypes){
        if (returnType != this.returnType || ! CollectionUtil.equals(formals, this.formals) || ! CollectionUtil.equals(throwTypes, this.throwTypes) || body != this.body || !CollectionUtil.equals(annotations, this.annotations) || !CollectionUtil.equals(paramTypes, this.paramTypes)) {
            JL5MethodDecl_c n = (JL5MethodDecl_c) copy();
            n.returnType = returnType;
            n.formals = TypedList.copyAndCheck(formals, Formal.class, true);
            n.throwTypes = TypedList.copyAndCheck(throwTypes, TypeNode.class, true);
            n.body = body;
            n.annotations = TypedList.copyAndCheck(annotations, AnnotationElem.class, true);
            n.paramTypes = paramTypes;
            return n;
        }
        return this;
                                                            
    }

    public NodeVisitor disambiguateEnter(AmbiguityRemover ar) throws SemanticException {
        if (ar.kind() == JL5AmbiguityRemover.TYPE_VARS) {
            return ar.bypass(formals).bypass(returnType).bypass(throwTypes).bypass(body);
        }
        else {
            return super.disambiguateEnter(ar);
        }
    }

    public Node disambiguate(AmbiguityRemover ar) throws SemanticException {
        if (ar.kind() == AmbiguityRemover.SIGNATURES) {
            Context c = ar.context();
            TypeSystem ts = ar.typeSystem();
            ParsedClassType ct = c.currentClassScope();
            JL5MethodInstance mi = (JL5MethodInstance)makeMethodInstance(ct, ts);
            List<TypeVariable> pTypes = new ArrayList<TypeVariable>();
            for (Iterator<ParamTypeNode> it = paramTypes.iterator(); it.hasNext(); ){
                TypeVariable tv = (TypeVariable) it.next().type();
                pTypes.add(tv);
            }
            mi.typeVariables(pTypes);
            return flags(mi.flags()).methodInstance(mi);
         }
         return this;
    }

    public Node typeCheck(TypeChecker tc) throws SemanticException {
        // check no duplicate annotations used
        JL5TypeSystem ts = (JL5TypeSystem)tc.typeSystem();
        ts.checkDuplicateAnnotations(annotations);
   
        // check throws clauses are not parameterized
        for (Iterator it = throwTypes.iterator(); it.hasNext(); ){
            TypeNode tn = (TypeNode)it.next();
            Type next = tn.type();
            if (next instanceof ParameterizedType){
                throw new SemanticException("Cannot use parameterized type "+next+" in a throws clause", tn.position());
            }
        }
        
        // check at most last formal is variable
        for (int i = 0; i < formals.size(); i++){
            JL5Formal f = (JL5Formal)formals.get(i);
            if (i != formals.size()-1 && f.isVariable()){
                throw new SemanticException("Only last formal can be variable in method declaration.", f.position());
            }
        }

        // repeat super class type checking so it can be specialized
        // to handle inner enum classes which indeed do have
        // static methods
        if (tc.context().currentClass().flags().isInterface()) {
            if (flags().isProtected() || flags().isPrivate()) {
                throw new SemanticException("Interface methods must be public.",
                                            position());
            }
        }

        try {
            ts.checkMethodFlags(flags());
        }
        catch (SemanticException e) {
            throw new SemanticException(e.getMessage(), position());
        }

	    if (body == null && ! (flags().isAbstract() || flags().isNative())) {
	        throw new SemanticException("Missing method body.", position());
	    }

	    if (body != null && flags().isAbstract()) {
	        throw new SemanticException(
		    "An abstract method cannot have a body.", position());
	    }

	    if (body != null && flags().isNative()) {
	        throw new SemanticException(
		    "A native method cannot have a body.", position());
	    }

        for (Iterator i = throwTypes().iterator(); i.hasNext(); ) {
            TypeNode tn = (TypeNode) i.next();
            Type t = tn.type();
            if (! t.isThrowable()) {
                throw new SemanticException("Type \"" + t +
                    "\" is not a subclass of \"" + ts.Throwable() + "\".",
                    tn.position());
            }
        }

        // check that inner classes do not declare static methods
        // unless class is enum
        if (flags().isStatic() && !JL5Flags.isEnumModifier(methodInstance().container().toClass().flags()) && 
              methodInstance().container().toClass().isInnerClass()) {
            // it's a static method in an inner class.
            throw new SemanticException("Inner classes cannot declare " + 
                    "static methods.", this.position());             
        }
        
        if (mi instanceof JL5MethodInstance) {
            JL5MethodInstance m = (JL5MethodInstance) mi;
            if (m.isGeneric()) {
                ((JL5TypeSystem)tc.typeSystem()).checkTVForwardReference(m.typeVariables());
            }
        };
        

        overrideMethodCheck(tc);

        return this;
    }

    public Node applicationCheck(ApplicationChecker appCheck) throws SemanticException {
        JL5TypeSystem ts = (JL5TypeSystem)appCheck.typeSystem();
        for( Iterator it = annotations.iterator(); it.hasNext(); ){
            AnnotationElem next = (AnnotationElem)it.next();
            ts.checkAnnotationApplicability(next, this);
        }
        return this;         
    }
    
    public Node visitChildren(NodeVisitor v){
        List annotations = visitList(this.annotations, v);
        List paramTypes = visitList(this.paramTypes, v);
        List formals = visitList(this.formals, v);
        TypeNode returnType = (TypeNode) visitChild(this.returnType, v);
        List throwTypes = visitList(this.throwTypes, v);
        Block body = (Block) visitChild(this.body, v);
        return reconstruct(returnType, formals, throwTypes, body, annotations, paramTypes);
    }
    
    public void translate(CodeWriter w, Translator tr){
        if (isCompilerGenerated()) return;
        
        for (Iterator it = annotations.iterator(); it.hasNext(); ){
            print((AnnotationElem)it.next(), w, tr);
        }

        super.translate(w, tr);
    }

    public void prettyPrintHeader(Flags flags, CodeWriter w, PrettyPrinter tr) {
        w.begin(0);
        w.write(flags.translate());

        if ((paramTypes != null) && !paramTypes.isEmpty()){
            w.write("<");
            for (Iterator it = paramTypes.iterator(); it.hasNext(); ){
                ParamTypeNode next = (ParamTypeNode)it.next();
                print(next, w, tr);
                if (it.hasNext()){
                    w.write(", ");
                }
            }
            w.write("> ");
        }
        
        print(returnType, w, tr);
        w.write(" " + name + "(");
        w.begin(0);

        for (Iterator i = formals.iterator(); i.hasNext(); ) {
            Formal f = (Formal) i.next();
            print(f, w, tr);

            if (i.hasNext()) {
                w.write(",");
                w.allowBreak(0, " ");
            }
        }

        w.end();
        w.write(")");

        if (! throwTypes().isEmpty()) {
            w.allowBreak(6);
            w.write("throws ");

            for (Iterator i = throwTypes().iterator(); i.hasNext(); ) {
                TypeNode tn = (TypeNode) i.next();
                print(tn, w, tr);

                if (i.hasNext()) {
                    w.write(",");
                    w.allowBreak(4, " ");
                }
            }
        }

        w.end();
    }


    public List runtimeAnnotations(){
        return runtimeAnnotations;
    }
    public List classAnnotations(){
        return classAnnotations;
    }
    public List sourceAnnotations(){
        return sourceAnnotations;
    }

    @Override
    public Context enterScope(Context c) {
        c = super.enterScope(c);
        for (ParamTypeNode pn : paramTypes) {
            c = ((JL5Context)c).addTypeVariable((TypeVariable)pn.type());
        }
        return c;
    }

    @Override
    public Node buildTypes(TypeBuilder tb) throws SemanticException {
        return super.buildTypes(tb);
    }
    
    
    
    
}
