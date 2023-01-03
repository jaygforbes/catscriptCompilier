package edu.montana.csci.csci468.parser;

import edu.montana.csci.csci468.parser.expressions.*;
import edu.montana.csci.csci468.parser.statements.*;
import edu.montana.csci.csci468.tokenizer.CatScriptTokenizer;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenList;
import edu.montana.csci.csci468.tokenizer.TokenType;
import org.apache.commons.lang.ObjectUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static edu.montana.csci.csci468.tokenizer.TokenType.*;

public class CatScriptParser {

    private TokenList tokens;
    private FunctionDefinitionStatement currentFunctionDefinition;

    public CatScriptProgram parse(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();

        // first parse an expression
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = null;
        try {
            expression = parseExpression();
        } catch(RuntimeException re) {
            // ignore :)
        }
        if (expression == null || tokens.hasMoreTokens()) {
            tokens.reset();
            while (tokens.hasMoreTokens()) {
                program.addStatement(parseProgramStatement());
            }
        } else {
            program.setExpression(expression);
        }

        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    public CatScriptProgram parseAsExpression(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = parseExpression();
        program.setExpression(expression);
        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    //============================================================
    //  Statements
    //============================================================


    private Statement parseProgramStatement() {
        Statement statement = parseFunctionDefinition();
        if (statement != null) {
            return statement;
        }
        return parseStatement();
    }

    private Statement parseStatement(){
        //initialize each statement
        Statement statement = parsePrintStatement();
        if(statement != null) {
            return statement;
        }
        statement = parseForStatement();
        if(statement != null) {
            return statement;
        }
        statement = parseIfStatement();
        if(statement != null) {
            return statement;
        }
        statement = parseVarStatement();
        if(statement != null) {
            return statement;
        }
        statement = parseAssignmentOrFunctionCall();
        if(statement != null) {
            return statement;
        }
        statement = parseReturnStatement();
        if(statement != null) {
            return statement;
        }
        return new SyntaxErrorStatement(tokens.consumeToken());
    }

    private Statement parsePrintStatement() {
        if(tokens.match(PRINT)){

            PrintStatement printStatement = new PrintStatement();
            printStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, printStatement);
            printStatement.setExpression((parseExpression()));
            printStatement.setEnd(require(RIGHT_PAREN, printStatement));

            return printStatement;
        } else {
            return null;
        }
    }

    private Statement parseForStatement(){
        if(tokens.match(FOR)){
            ForStatement forStatement = new ForStatement();
            forStatement.setStart(tokens.consumeToken());
            require(LEFT_PAREN, forStatement);
            forStatement.setVariableName(tokens.consumeToken().getStringValue());
            require(IN, forStatement);
            forStatement.setExpression(parseExpression());
            require(RIGHT_PAREN, forStatement);
            require(LEFT_BRACE, forStatement);
            List<Statement> body = new LinkedList<>();
            while(!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens()){
                body.add(parseStatement());
            }
            forStatement.setBody(body);
            forStatement.setEnd(require(RIGHT_BRACE, forStatement));
            return forStatement;
        }
        return null;
    }


    private Statement parseIfStatement(){
        //Handle initial IF
        if(tokens.match(IF)){
            IfStatement ifStatement = new IfStatement();
            ifStatement.setStart(tokens.consumeToken());
            require(LEFT_PAREN, ifStatement);
            Expression parsedBool = parseExpression();
            ifStatement.setExpression(parsedBool);
            require(RIGHT_PAREN, ifStatement);
            require(LEFT_BRACE, ifStatement);
            List<Statement> statements = new LinkedList<>();
            while(!tokens.match(RIGHT_BRACE)){
                statements.add(parseStatement());
                if(tokens.match(EOF)){
                    break;
                }
            }
            ifStatement.setTrueStatements(statements);
            require(RIGHT_BRACE, ifStatement);
            // Handle else
            if(tokens.match(ELSE)){
                tokens.consumeToken();
                require(LEFT_BRACE, ifStatement);
                if(tokens.match(EOF)){
                    ifStatement.addError(ErrorType.UNTERMINATED_ARG_LIST);
                    return ifStatement;
                }
                //checks to see for else if case and parses
                if(tokens.match(IF)){
                    parseIfStatement();
                }
                else {
                    if(tokens.match(EOF)){
                        ifStatement.addError(ErrorType.UNTERMINATED_ARG_LIST);
                    }
                    //collect else
                    List<Statement> elseStatements = new LinkedList<>();
                    while(!tokens.match(RIGHT_BRACE)){
                        elseStatements.add(parseStatement());
                        if(tokens.match(EOF)){
                            break;
                        }
                    }
                    ifStatement.setElseStatements(elseStatements);
                    require(RIGHT_BRACE, ifStatement);
                }
            }
            return ifStatement;
        }
        return null;
    }

    private Statement parseVarStatement(){
        if (tokens.match(VAR)){
            VariableStatement variableStatement = new VariableStatement();
            variableStatement.setStart(tokens.consumeToken());
            final Token tokenName = require(IDENTIFIER, variableStatement);
            variableStatement.setVariableName(tokenName.getStringValue());
            if (tokens.matchAndConsume(COLON)){
                TypeLiteral typeLiteral = parseTypeLiteral();
                variableStatement.setExplicitType(typeLiteral.getType());
            }
            require(EQUAL, variableStatement);
            variableStatement.setExpression(parseExpression());
            return variableStatement;
        }
        return null;
    }

    private Statement parseAssignmentOrFunctionCall(){
        //first part handles assigment
        if(tokens.match(IDENTIFIER)){
            Token start = tokens.consumeToken();
            if(tokens.match(EQUAL)){
                tokens.consumeToken();

                final AssignmentStatement assignmentStatement = new AssignmentStatement();
                assignmentStatement.setStart(start);
                assignmentStatement.setVariableName(start.getStringValue());
                assignmentStatement.setExpression(parseExpression());
                assignmentStatement.setEnd(tokens.lastToken());

                return assignmentStatement;
                //function call part
            }else if (tokens.matchAndConsume(LEFT_PAREN)){
                FunctionCallExpression expression = (FunctionCallExpression) parseFunctionCallExpression(start);
                final FunctionCallStatement functionCallStatement = new FunctionCallStatement(expression);
                functionCallStatement.setStart(start);
                functionCallStatement.setEnd(tokens.lastToken());
                return functionCallStatement;
            }else {
                return new SyntaxErrorStatement(tokens.consumeToken());
            }
        } else {
            return null;
        }
    }

    //Done during lecture
    private FunctionDefinitionStatement parseFunctionDefinition() {
        if (tokens.match(FUNCTION)) {
            FunctionDefinitionStatement func = new FunctionDefinitionStatement();
            func.setStart(tokens.consumeToken());

            Token functionName = require(IDENTIFIER, func);

            func.setName(functionName.getStringValue());

            require(LEFT_PAREN, func);
            if (!tokens.match(RIGHT_PAREN)) {
                do {
                    Token paramName = require(IDENTIFIER, func);
                    TypeLiteral typeLiteral = null;
                    if (tokens.matchAndConsume(COLON)) {
                        typeLiteral = parseTypeLiteral();
                    }
                    func.addParameter(paramName.getStringValue(), typeLiteral);
                } while (tokens.matchAndConsume(COMMA));
            }
            require(RIGHT_PAREN, func);

            TypeLiteral typeLiteral = null;
            if (tokens.matchAndConsume(COLON)) {
                typeLiteral = parseTypeLiteral();
            }
            func.setType(typeLiteral);

            currentFunctionDefinition = func;

            require(LEFT_BRACE, func);
            LinkedList<Statement> statements = new LinkedList<>();

            while (!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens()) {
                statements.add(parseStatement());
            }
            currentFunctionDefinition = null;

            require(RIGHT_BRACE, func);
            func.setBody(statements);

            return func;
        } else {
            return null;
        }
    }

    private Statement parseReturnStatement() {
        if (tokens.match(RETURN)){
            ReturnStatement returnStatement = new ReturnStatement();
            returnStatement.setFunctionDefinition(this.currentFunctionDefinition);
            returnStatement.setStart(tokens.consumeToken());
            if (!tokens.match(RIGHT_BRACE)){
                final Expression parseExpression = parseExpression();
                returnStatement.setExpression(parseExpression);
            }
            returnStatement.setEnd(tokens.lastToken());
            return returnStatement;
        } else {
            return null;
        }
    }

    // Done during lecture
    private TypeLiteral parseTypeLiteral() {
        if (tokens.match("string")){
            TypeLiteral typeLiteral = new TypeLiteral();
            typeLiteral.setType(CatscriptType.STRING);
            typeLiteral.setToken(tokens.consumeToken());
            return typeLiteral;
        }
        if (tokens.match("int")){
            TypeLiteral typeLiteral = new TypeLiteral();
            typeLiteral.setType(CatscriptType.INT);
            typeLiteral.setToken(tokens.consumeToken());
            return typeLiteral;
        }
        if (tokens.match("bool")){
            TypeLiteral typeLiteral = new TypeLiteral();
            typeLiteral.setType(CatscriptType.BOOLEAN);
            typeLiteral.setToken(tokens.consumeToken());
            return typeLiteral;
        }
        if (tokens.match("object")){
            TypeLiteral typeLiteral = new TypeLiteral();
            typeLiteral.setType(CatscriptType.OBJECT);
            typeLiteral.setToken(tokens.consumeToken());
            return typeLiteral;
        }
        if (tokens.match("list")){
            TypeLiteral typeLiteral = new TypeLiteral();
            typeLiteral.setType(CatscriptType.getListType(CatscriptType.OBJECT));
            typeLiteral.setToken(tokens.consumeToken());
            if(tokens.matchAndConsume(LESS)){
                TypeLiteral componentType = parseTypeLiteral();
                typeLiteral.setType(CatscriptType.getListType(componentType.getType()));
                require(GREATER, typeLiteral);
            }
            return typeLiteral;
        }
        TypeLiteral typeLiteral = new TypeLiteral();
        typeLiteral.setType(CatscriptType.OBJECT);
        typeLiteral.setToken(tokens.consumeToken());
        typeLiteral.addError(ErrorType.BAD_TYPE_NAME);
        return typeLiteral;
    }






    //============================================================
    //  Expressions
    //============================================================

    private Expression parseExpression() {

        return parseEqualityExpression();

    }

    private Expression parseEqualityExpression() {
        Expression expression = parseComparisonExpression();
        while (tokens.match(EQUAL_EQUAL, BANG_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseComparisonExpression();
            EqualityExpression equalityExpression = new EqualityExpression(operator, expression, rightHandSide);
            equalityExpression.setStart(expression.getStart());
            equalityExpression.setEnd(rightHandSide.getEnd());
            expression = equalityExpression;
        }
        return expression;
    }

    private Expression parseComparisonExpression() {
        Expression expression = parseAdditiveExpression();
        while (tokens.match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseAdditiveExpression();
            ComparisonExpression comparisonExpression = new ComparisonExpression(operator, expression, rightHandSide);
            comparisonExpression.setStart(expression.getStart());
            comparisonExpression.setEnd(rightHandSide.getEnd());
            expression = comparisonExpression;
        }
        return expression;
    }
    private Expression parseAdditiveExpression() {
        Expression expression = parseFactorExpression();
        while (tokens.match(PLUS, MINUS)) {
            Token operator = tokens.consumeToken();
            Expression rightHandSide = parseFactorExpression();
            AdditiveExpression additiveExpression = new AdditiveExpression(operator, expression, rightHandSide);
            additiveExpression.setStart(expression.getStart());
            additiveExpression.setEnd(rightHandSide.getEnd());
            expression = additiveExpression;
        }
        return expression;
    }

    private Expression parseFactorExpression() {
        Expression expression = parseUnaryExpression();
        while (tokens.match(STAR, SLASH)) {
            Token operator = tokens.consumeToken();
            Expression rightHandSide = parseUnaryExpression();
            FactorExpression factorExpression = new FactorExpression(operator, expression, rightHandSide);
            factorExpression.setStart(expression.getStart());
            factorExpression.setEnd(rightHandSide.getEnd());
            expression = factorExpression;
        }
        return expression;
    }

    private Expression parseUnaryExpression() {
        if (tokens.match(MINUS, NOT)) {
            Token token = tokens.consumeToken();
            Expression rhs = parseUnaryExpression();
            UnaryExpression unaryExpression = new UnaryExpression(token, rhs);
            unaryExpression.setStart(token);
            unaryExpression.setEnd(rhs.getEnd());
            return unaryExpression;
        } else {
            return parsePrimaryExpression();
        }
    }

    private Expression parsePrimaryExpression(){
        if (tokens.match(INTEGER)) {
            Token intToken = tokens.consumeToken();
            IntegerLiteralExpression intExpression = new IntegerLiteralExpression(intToken.getStringValue());
            intExpression.setToken(intToken);
            return intExpression;
        } else if (tokens.match(STRING)) {
            Token stringToken = tokens.consumeToken();
            StringLiteralExpression stringExpression = new StringLiteralExpression(stringToken.getStringValue());
            stringExpression.setToken(stringToken);
            return stringExpression;
        } else if (tokens.match(NULL)) {
            Token nullToken = tokens.consumeToken();
            NullLiteralExpression nullExpression = new NullLiteralExpression();
            nullExpression.setToken(nullToken);
            return nullExpression;
        } else if (tokens.match(IDENTIFIER)) {
                Token identifierToken = tokens.consumeToken();
                if (tokens.match(LEFT_PAREN)){
                    return parseFunctionCallExpression(identifierToken);
                } else {
                    IdentifierExpression identifierExpression = new IdentifierExpression(identifierToken.getStringValue());
                    identifierExpression.setToken(identifierToken);
                    return identifierExpression;
                }
        } else if (tokens.match(TRUE)) {
            Token trueToken = tokens.consumeToken();
            BooleanLiteralExpression trueExpression = new BooleanLiteralExpression(true);
            trueExpression.setToken(trueToken);
            return trueExpression;
        } else if (tokens.match(FALSE)) {
            Token falseToken = tokens.consumeToken();
            BooleanLiteralExpression falseExpression = new BooleanLiteralExpression(false);
            falseExpression.setToken(falseToken);
            return falseExpression;
        } else if (tokens.match(LEFT_BRACKET)) {
            Token start = tokens.consumeToken();
            List<Expression> contents = new LinkedList<>();
            if(!tokens.match(RIGHT_BRACKET)){
                do {
                    contents.add(parseExpression());
                } while(tokens.matchAndConsume(COMMA) && tokens.hasMoreTokens());
            }
            ListLiteralExpression listLiteralExpression = new ListLiteralExpression(contents);
            listLiteralExpression.setStart(start);
            listLiteralExpression.setEnd(require(RIGHT_BRACKET, listLiteralExpression, ErrorType.UNTERMINATED_LIST));
            return listLiteralExpression;
        }  else if (tokens.match(LEFT_PAREN)) {
            Token start = tokens.consumeToken();
            ParenthesizedExpression parenthesizedExpression = new ParenthesizedExpression(parseExpression());
            parenthesizedExpression.setStart(start);
            if (tokens.match(RIGHT_PAREN)){
                parenthesizedExpression.setEnd(tokens.consumeToken());
            } else {
                parenthesizedExpression.setEnd(tokens.getCurrentToken());
                parenthesizedExpression.addError(ErrorType.UNTERMINATED_LIST);
            }
            return parenthesizedExpression;
        } else {

            SyntaxErrorExpression syntaxErrorExpression = new SyntaxErrorExpression(tokens.consumeToken());
            syntaxErrorExpression.setToken(tokens.consumeToken());
            return syntaxErrorExpression;
        }
    }


    private Expression parseFunctionCallExpression(Token start) {
        List<Expression> values = new LinkedList<>();
        tokens.matchAndConsume(LEFT_PAREN);
        if (!tokens.match(RIGHT_PAREN)) {
            do {

                values.add(parseExpression());

            } while (tokens.matchAndConsume(COMMA) && tokens.hasMoreTokens());
        }
        FunctionCallExpression expr = new FunctionCallExpression(start.getStringValue(), values);
        expr.setStart(start);
        expr.setEnd(require(RIGHT_PAREN, expr, ErrorType.UNTERMINATED_ARG_LIST));
        return expr;
    }





    //============================================================
    //  Parse Helpers
    //============================================================
    private Token require(TokenType type, ParseElement elt) {
        return require(type, elt, ErrorType.UNEXPECTED_TOKEN);
    }

    private Token require(TokenType type, ParseElement elt, ErrorType msg) {
        if(tokens.match(type)){
            return tokens.consumeToken();
        } else {
            elt.addError(msg, tokens.getCurrentToken());
            return tokens.getCurrentToken();
        }
    }

}
