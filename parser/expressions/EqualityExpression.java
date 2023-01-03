package edu.montana.csci.csci468.parser.expressions;

import edu.montana.csci.csci468.bytecode.ByteCodeGenerator;
import edu.montana.csci.csci468.eval.CatscriptRuntime;
import edu.montana.csci.csci468.parser.CatscriptType;
import edu.montana.csci.csci468.parser.SymbolTable;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenType;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;

import java.util.Objects;

public class EqualityExpression extends Expression {

    private final Token operator;
    private final Expression leftHandSide;
    private final Expression rightHandSide;

    public EqualityExpression(Token operator, Expression leftHandSide, Expression rightHandSide) {
        this.leftHandSide = addChild(leftHandSide);
        this.rightHandSide = addChild(rightHandSide);
        this.operator = operator;
    }

    public Expression getLeftHandSide() {
        return leftHandSide;
    }

    public Expression getRightHandSide() {
        return rightHandSide;
    }

    @Override
    public String toString() {
        return super.toString() + "[" + operator.getStringValue() + "]";
    }

    public boolean isEqual() {
        return operator.getType().equals(TokenType.EQUAL_EQUAL);
    }

    @Override
    public void validate(SymbolTable symbolTable) {
        leftHandSide.validate(symbolTable);
        rightHandSide.validate(symbolTable);
    }

    @Override
    public CatscriptType getType() {
        return CatscriptType.BOOLEAN;
    }

    //==============================================================
    // Implementation
    //==============================================================

    @Override
    public Object evaluate(CatscriptRuntime runtime) {
        if(operator.equals("==")){
            return getLeftHandSide() == getRightHandSide();
        }
        else{
            return getLeftHandSide() != getRightHandSide();
        }
    }

    @Override
    public void transpile(StringBuilder javascript) {
        super.transpile(javascript);
    }

    @Override
    public void compile(ByteCodeGenerator code) {
        Label setToFalse = new Label();
        Label end = new Label();
        getLeftHandSide().compile(code);
        box(code, getLeftHandSide().getType());

        getRightHandSide().compile(code);
        box(code, getRightHandSide().getType());

        code.addMethodInstruction(Opcodes.INVOKESTATIC, ByteCodeGenerator.internalNameFor(Objects.class), "equals",
                "(Ljava/lang/Object;Ljava/lang/Object;)Z");

        if(!isEqual()){
            code.addJumpInstruction(Opcodes.IFNE, setToFalse);
            code.pushConstantOntoStack(true);
            code.addJumpInstruction(Opcodes.GOTO, end);
            code.addLabel(setToFalse);
            code.pushConstantOntoStack(false);
            code.addLabel(end);
        }
    }


}
