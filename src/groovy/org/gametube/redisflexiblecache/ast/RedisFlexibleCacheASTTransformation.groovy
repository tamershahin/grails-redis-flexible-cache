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

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.GroovyASTTransformation

/**
 * AST transformation for the EvictRedisFlexibleCache annotation
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class RedisFlexibleCacheASTTransformation extends AbstractRedisFlexibleASTTransformation {

    @Override
    protected void generateDoCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map doCacheProperties) {
        def expire = astNodes[0]?.members?.expire?.text
        def keyString = astNodes[0]?.members?.key?.text
        def group = astNodes[0]?.members?.group?.text ?: ''
        def reAttachToSession = astNodes[0]?.members?.reAttachToSession?.value ?: false

        if (!validateDoCacheProperties(astNodes, sourceUnit, keyString, expire, group, reAttachToSession)) {
            return
        }

        doCacheProperties.put(KEY, keyString)
        doCacheProperties.put(EXPIRE, expire)
        doCacheProperties.put(GROUP, group)
        doCacheProperties.put(REATTACH_TO_SESSION, reAttachToSession)
    }

    private Boolean validateDoCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, keyString, expire, String group, reAttachToSession) {

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
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map doCacheProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        addRedisServiceDoCacheKeyExpression(doCacheProperties, argumentListExpression)
        if (doCacheProperties.containsKey(GROUP)) {
            argumentListExpression.addExpression(makeConstantExpression(doCacheProperties.get(GROUP)))
        }
        if (doCacheProperties.containsKey(EXPIRE)) {
            if (doCacheProperties.get(EXPIRE).toString().isNumber()) {
                argumentListExpression.addExpression(makeConstantExpression(doCacheProperties.get(EXPIRE).toString().toInteger()))
            } else {
                argumentListExpression.addExpression(ConstantExpression.NULL)
            }
        }
        if (doCacheProperties.containsKey(REATTACH_TO_SESSION)) {
            argumentListExpression.addExpression(makeConstantExpression(doCacheProperties.get(REATTACH_TO_SESSION)))
        }
        argumentListExpression
    }
}