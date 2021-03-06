/*
 * Copyright 2020 Manuel Wrage
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ivianuu.injekt.compiler

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.util.slicedMap.BasicWritableSlice
import org.jetbrains.kotlin.util.slicedMap.RewritePolicy
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

object InjektWritableSlices {
    val IS_IMPLICIT: WritableSlice<Any, Boolean> =
        BasicWritableSlice(RewritePolicy.DO_NOTHING)
    val IS_READER_LAMBDA_INVOKE: WritableSlice<IrFunctionAccessExpression, Boolean> =
        BasicWritableSlice(RewritePolicy.DO_NOTHING)
    val IS_TRANSFORMED_IMPLICIT_FUNCTION: WritableSlice<IrSimpleFunction, Boolean> =
        BasicWritableSlice(RewritePolicy.DO_NOTHING)
    val RUN_CHILD_READER_METADATA: WritableSlice<IrCall, RunChildReaderMetadata> =
        BasicWritableSlice(RewritePolicy.DO_NOTHING)
}

data class RunChildReaderMetadata(
    val callingContext: IrClass,
    val contextExpression: IrExpression
)
