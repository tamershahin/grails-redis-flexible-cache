package org.gametube.cache.redis.ast

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RedisFlexibleCacheASTTransformation extends AbstractRedisFlexibleASTTransformation {

    @Override
    protected void generateQuickCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map quickCacheProperties) {
        def expire = astNodes[0]?.members?.expire?.text ?: '-1'
        def keyString = astNodes[0]?.members?.key?.text
        def group = astNodes[0]?.members?.group?.text ?: ''
        def reAttachToSession = astNodes[0]?.members?.reAttachToSession?.value ?: false

        if (!validateQuickCacheProperties(astNodes, sourceUnit, keyString, expire, group, reAttachToSession)) {
            return
        }

        quickCacheProperties.put(KEY, keyString)
        quickCacheProperties.put(EXPIRE, expire)
        quickCacheProperties.put(GROUP, group)
        quickCacheProperties.put(REATTACH_TO_SESSION, reAttachToSession)
    }

    private Boolean validateQuickCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, keyString, expire, String group, reAttachToSession) {

        if (!keyString || keyString.class != String) {
            addError('Internal Error: annotation does not contain key property', astNodes[0], sourceUnit)
            return false
        }

        if (expire && expire.class != String && !Integer.parseInt(expire)) {
            addError('Internal Error: provided expire is not an String (in millis)', astNodes[0], sourceUnit)
            return false
        }

        if (group && group.class != String) {
            addError('Internal Error: provided group is not an String', astNodes[0], sourceUnit)
            return false
        }

        if (reAttachToSession.class != Boolean) {
            addError('Internal Error: provided reAttachToSession is not an boolean', astNodes[0], sourceUnit)
            return false
        }
        true
    }

    @Override
    protected ConstantExpression makeCacheServiceConstantExpression() {
        new ConstantExpression('doCache')
    }

    @Override
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map quickCacheProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        addRedisServiceQuickCacheKeyExpression(quickCacheProperties, argumentListExpression)
        if (quickCacheProperties.containsKey(GROUP)) {
            argumentListExpression.addExpression(makeConstantExpression(quickCacheProperties.get(GROUP)))
        }
        if (quickCacheProperties.containsKey(EXPIRE)) {
            argumentListExpression.addExpression(makeConstantExpression(Integer.parseInt(quickCacheProperties.get(EXPIRE).toString())))
        }
        if (quickCacheProperties.containsKey(REATTACH_TO_SESSION)) {
            argumentListExpression.addExpression(makeConstantExpression(quickCacheProperties.get(REATTACH_TO_SESSION)))
        }
        argumentListExpression
    }
}