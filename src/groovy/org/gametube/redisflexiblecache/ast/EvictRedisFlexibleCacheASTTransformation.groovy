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
class EvictRedisFlexibleCacheASTTransformation extends AbstractRedisFlexibleASTTransformation {

    @Override
    protected void generateDoCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, Map doCacheProperties) {
        def keyString = astNodes[0]?.members?.key?.text

        if (!validateDoCacheProperties(astNodes, sourceUnit, keyString)) {
            return
        }

        doCacheProperties.put(KEY, keyString)
    }

    private Boolean validateDoCacheProperties(ASTNode[] astNodes, SourceUnit sourceUnit, keyString) {

        if (!keyString || keyString.class != String) {
            addError('Internal Error: annotation does not contain key property', astNodes[0], sourceUnit)
            return false
        }

        true
    }

    @Override
    protected ConstantExpression makeCacheServiceConstantExpression() {
        new ConstantExpression('evictCache')
    }

    @Override
    protected ArgumentListExpression makeRedisServiceArgumentListExpression(Map doCacheProperties) {
        ArgumentListExpression argumentListExpression = new ArgumentListExpression()
        addRedisServiceDoCacheKeyExpression(doCacheProperties, argumentListExpression)
        argumentListExpression
    }
}