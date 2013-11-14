/*
 * Copyright 2006-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gametube.redisflexiblecache.ast

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.classgen.VariableScopeVisitor
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.gametube.redisflexiblecache.RedisFlexibleCacheService

import static org.springframework.asm.Opcodes.ACC_PRIVATE
import static org.springframework.asm.Opcodes.ACC_PUBLIC

/**
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
abstract class AbstractRedisFlexibleASTTransformation implements ASTTransformation {

    protected static final String KEY = 'key'
    protected static final String EXPIRE = 'expire'
    protected static final String GROUP = 'group'
    protected static final String REATTACH_TO_SESSION = 'reAttachToSession'
    protected static final String HASH_CODE = '#'
    protected static final String GSTRING = '$'
    protected static final String CACHE_SERVICE = 'redisFlexibleCacheService'

    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        //map to hold the params we will pass to the quickCache[?] method
        def quickCacheProperties = [:]

        try {
            injectCacheService(sourceUnit)
            generateQuickCacheProperties(astNodes, sourceUnit, quickCacheProperties)
            //if the key is missing there is an issue with the annotation
            if (!quickCacheProperties.containsKey(KEY) || !quickCacheProperties.get(KEY)) {
                return
            }
            addQuickCachedStatements((MethodNode) astNodes[1], quickCacheProperties)
            visitVariableScopes(sourceUnit)
        } catch (Exception e) {
            addError("Error during RedisFlexibleCache AST Transformation: ${e}", astNodes[0], sourceUnit)
            throw e
        }
    }

    /**
     * Create the statements for the quickCached method, clear the node and then readd the quickCached code back to the method.
     * @param methodNode The MethodNode we will be clearing and replacing with the cacheService.doCache[?] method call with.
     * @param quickCacheProperties The map of properties to use for the service invocation
     */
    private void addQuickCachedStatements(MethodNode methodNode, LinkedHashMap quickCacheProperties) {
        def stmt = quickCacheMethod(methodNode, quickCacheProperties)
        methodNode.code.statements.clear()
        methodNode.code.statements.addAll(stmt)
    }

    /**
     * Fix the variable scopes for closures.  Without this closures will be missing the input params being passed from the parent scope.
     * @param sourceUnit The SourceUnit to visit and add the variable scopes.
     */
    private void visitVariableScopes(SourceUnit sourceUnit) {
        VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(sourceUnit);
        sourceUnit.AST.classes.each {
            scopeVisitor.visitClass(it)
        }
    }

    /**
     * Determine if the user missed injecting the cacheService into the class with the @QuickCached method.
     * @param sourceUnit SourceUnit to detect and/or inject service into
     */
    private void injectCacheService(SourceUnit sourceUnit) {
        if (!((ClassNode) sourceUnit.AST.classes.toArray()[0]).properties?.any { it?.field?.name == CACHE_SERVICE }) {
//            println "Adding cacheService to class ${sourceUnit.AST.classes[0].name}."
            if (!sourceUnit.AST.imports.any { it.className == ClassHelper.make(RedisFlexibleCacheService).name }
                    && !sourceUnit.AST.starImports.any { it.packageName == "${ClassHelper.make(RedisFlexibleCacheService).packageName}." }) {
//                println "Adding namespace ${ClassHelper.make(RedisFlexibleCacheService).packageName} to class ${sourceUnit.AST.classes[0].name}."
                sourceUnit.AST.addImport('RedisFlexibleCacheService', ClassHelper.make(RedisFlexibleCacheService))
            }
            addRedisServiceProperty((ClassNode) sourceUnit.AST.classes.toArray()[0], CACHE_SERVICE)
        }
    }

    /**
     * This method adds a new property to the class. Groovy automatically handles adding the getters and setters so you
     * don't have to create special methods for those.  This could be reused for other properties.
     * @param cNode Node to inject property onto.  Usually a ClassNode for the current class.
     * @param propertyName The name of the property to inject.
     * @param propertyType The object class of the property. (defaults to Object.class)
     * @param initialValue Initial value of the property. (defaults null)
     */
    private void addRedisServiceProperty(ClassNode cNode, String propertyName, Class propertyType = java.lang.Object.class, Expression initialValue = null) {
        FieldNode field = new FieldNode(
                propertyName,
                ACC_PRIVATE,
                new ClassNode(propertyType),
                new ClassNode(cNode.class),
                initialValue
        )

        cNode.addProperty(new PropertyNode(field, ACC_PUBLIC, null, null))
    }

    /**
     * method to add the key and expires and options if they exist
     * @param astNodes the ast nodes
     * @param sourceUnit the source unit
     * @param quickCacheProperties map to put data in
     * @return
     */
    protected abstract void generateQuickCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map quickCacheProperties)

    protected abstract ConstantExpression makeCacheServiceConstantExpression()

    protected abstract ArgumentListExpression makeRedisServiceArgumentListExpression(Map quickCacheProperties)

    protected List<Statement> quickCacheMethod(MethodNode methodNode, Map quickCacheProperties) {
        BlockStatement body = new BlockStatement()
//        addInterceptionLogging(body, 'quickCached method')
        addRedisServiceQuickCacheInvocation(body, methodNode, quickCacheProperties)
        body.statements
    }

    protected void addRedisServiceQuickCacheInvocation(BlockStatement body, MethodNode methodNode, Map quickCacheProperties) {
        ArgumentListExpression argumentListExpression = makeRedisServiceArgumentListExpression(quickCacheProperties)
        argumentListExpression.addExpression(makeClosureExpression(methodNode))

        body.addStatement(
                new ReturnStatement(
                        new MethodCallExpression(
                                new VariableExpression(CACHE_SERVICE),
                                makeCacheServiceConstantExpression(),
                                argumentListExpression
                        )
                )
        )
    }

    protected void addRedisServiceQuickCacheKeyExpression(Map quickCacheProperties, ArgumentListExpression argumentListExpression) {
        if (quickCacheProperties.get(KEY).toString().contains(HASH_CODE)) {
            def ast = new AstBuilder().buildFromString("""
                "${quickCacheProperties.get(KEY).toString().replace(HASH_CODE, GSTRING).toString()}"
           """)
            argumentListExpression.addExpression(ast[0].statements[0].expression)
        } else {
            argumentListExpression.addExpression(new ConstantExpression(quickCacheProperties.get(KEY).toString()))
        }
    }

    protected ClosureExpression makeClosureExpression(MethodNode methodNode) {
        ClosureExpression closureExpression = new ClosureExpression(
                [] as Parameter[],
                new BlockStatement(methodNode.code.statements as Statement[], new VariableScope())
        )
        closureExpression.variableScope = new VariableScope()
        closureExpression
    }

    protected ConstantExpression makeConstantExpression(constantExpression) {
        new ConstantExpression(constantExpression)
    }

    protected void addError(String msg, ASTNode node, SourceUnit source) {
        int line = node.lineNumber
        int col = node.columnNumber
        SyntaxException se = new SyntaxException("${msg}\n", line, col)
        SyntaxErrorMessage sem = new SyntaxErrorMessage(se, source)
        source.errorCollector.addErrorAndContinue(sem)
    }
}
