package edu.montana.csci.csci468.parser.expressions;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.SymbolTable;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static edu.montana.csci.csci468.bytecode.ByteCodeGenerator.internalNameFor;

public class ListLiteralExpression extends Expression {
    List<Expression> values;
    private CatscriptType type;

    public ListLiteralExpression(List<Expression> values) {
        this.values = new LinkedList<>();
        for (Expression value : values) {
            this.values.add(addChild(value));
        }
    }

    public List<Expression> getValues() {
        return values;
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        for (Expression value : values) {
            value.validate(symbolTable);
        }
        if (values.size() > 0) {
            // TODO - generalize this looking at all objects in list
            type = CatscriptType.getListType(values.get(0).getType());
        } else {
            type = CatscriptType.getListType(CatscriptType.OBJECT);
        }
    }

    @Override
    public CatscriptType getType() {
        return type;
    }

    //==============================================================
    // Implementation
    //==============================================================

    @Override
    public Object evaluate(CatscriptRuntime runtime) {
        ArrayList<Object> vals = new ArrayList<>();
        for (Expression value : values) {
            vals.add(value.evaluate(runtime));

        }
        return vals;
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    //partly done in class
    public void compile(ByteCodeGenerator code) {
        code.addTypeInstruction(Opcodes.NEW, internalNameFor(LinkedList.class));
        code.addInstruction(Opcodes.DUP);
        code.addMethodInstruction(Opcodes.INVOKESPECIAL,internalNameFor(LinkedList.class),"<init>","()V");
        values.forEach(value -> {
            code.addInstruction(Opcodes.DUP);
            value.compile(code);

            //boxing
            //invoke add()
            //do something with add() return
            box(code, value.getType());
            code.addMethodInstruction(Opcodes.INVOKEVIRTUAL, internalNameFor(LinkedList.class),"add","(Ljava/lang/Object;)Z");
            code.addInstruction(Opcodes.POP);
        });
    }


}
