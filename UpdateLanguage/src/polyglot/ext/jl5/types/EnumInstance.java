package polyglot.ext.jl5.types;

import polyglot.types.FieldInstance;
import polyglot.types.MemberInstance;
import polyglot.types.ParsedClassType;

public interface EnumInstance extends FieldInstance, MemberInstance {
/*
    EnumInstance flags(Flags flags);

    EnumInstance name(String name);

    EnumInstance type(Type type);

    EnumInstance container(ReferenceType container);*/

    ParsedClassType anonType();
}

