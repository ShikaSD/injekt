package com.ivianuu.injekt.compiler

import com.ivianuu.injekt.Lazy
import com.ivianuu.injekt.Provider
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Test

class SetTest {

    @Test
    fun testSet() = codegen(
        """
        interface TestComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun create(): TestComponent {
            transient { CommandA() }
            transient { CommandB() }
            transient { CommandC() }
            set<Command> {
                add<CommandA>()
                add<CommandB>()
                add<CommandC>()
            }
            return createImpl()
        }
        
        fun invoke() = create().set
    """
    ) {
        val set = invokeSingleFile<Set<Command>>().toList()
        assertEquals(3, set.size)
        assertTrue(set[0] is CommandA)
        assertTrue(set[1] is CommandB)
        assertTrue(set[2] is CommandC)
    }

    @Test
    fun testSetOfProvider() = codegen(
        """
        interface TestComponent {
            val set: Set<@Provider () -> Command>
        }
        
        @Factory
        fun create(): TestComponent {
            transient { CommandA() }
            transient { CommandB() }
            transient { CommandC() }
            set<Command> {
                add<CommandA>()
                add<CommandB>()
                add<CommandC>()
            }
            return createImpl()
        }
        
        fun invoke() = create().set
    """
    ) {
        val set = invokeSingleFile<Set<@Provider () -> Command>>().toList()
        assertEquals(3, set.size)
        assertTrue(set[0]() is CommandA)
        assertTrue(set[1]() is CommandB)
        assertTrue(set[2]() is CommandC)
    }

    @Test
    fun testSetOfLazy() = codegen(
        """
        interface TestComponent {
            val set: Set<@Lazy () -> Command>
        }
        
        @Factory
        fun create(): TestComponent {
            transient { CommandA() }
            transient { CommandB() }
            transient { CommandC() }
            set<Command> {
                add<CommandA>()
                add<CommandB>()
                add<CommandC>()
            }
            return createImpl()
        }
        
        fun invoke() = create().set
    """
    ) {
        val set = invokeSingleFile<Set<@Lazy () -> Command>>().toList()
        assertEquals(3, set.size)
        assertTrue(set[0]() is CommandA)
        assertTrue(set[1]() is CommandB)
        assertTrue(set[2]() is CommandC)
    }

    @Test
    fun testEmptySet() = codegen(
        """
        interface TestComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun create(): TestComponent {
            set<Command>()
            return createImpl()
        }
        
        fun invoke() = create().set
    """
    ) {
        val set = invokeSingleFile<Set<Command>>().toList()
        assertEquals(0, set.size)
    }

    @Test
    fun testSingleElementSet() = codegen(
        """
        interface TestComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun create(): TestComponent {
            transient { CommandA() }
            set<Command> {
                add<CommandA>()
            }
            return createImpl()
        }
        
        fun invoke() = create().set
    """
    ) {
        val set = invokeSingleFile<Set<Command>>().toList()
        assertEquals(1, set.size)
    }

    @Test
    fun testSetOverridesFails() = codegen(
        """
        interface TestComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun create(): TestComponent {
            transient { CommandA() }
            transient { CommandB() }
            set<Command> {
                add<CommandA>()
                add<CommandA>()
            }
            return createImpl()
        }
    """
    ) {
        assertInternalError("already bound")
    }

    @Test
    fun testNestedSet() = codegen(
        """
        interface ParentComponent {
            val set: Set<Command>
            val childFactory: @ChildFactory () -> ChildComponent
        }
        
        interface ChildComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun createParent(): ParentComponent {
            transient { CommandA() }
            set<Command> {
                add<CommandA>()
            }
            childFactory(::createChild)
            return createImpl()
        }
        
        @ChildFactory
        fun createChild(): ChildComponent {
            transient { CommandB() }
            set<Command> {
                add<CommandB>()
            }
            return createImpl()
        }
        
        fun invoke() = createParent().childFactory().set
    """
    ) {
        val set = invokeSingleFile<Set<Command>>().toList()
        assertEquals(2, set.size)
        assertTrue(set[0] is CommandA)
        assertTrue(set[1] is CommandB)
    }

    @Test
    fun testNestedOverrideFails() = codegen(
        """
        interface ParentComponent {
            val set: Set<Command>
            val childFactory: @ChildFactory () -> ChildComponent
        }
        
        interface ChildComponent {
            val set: Set<Command>
        }
        
        @Factory
        fun createParent(): ParentComponent {
            transient { CommandA() }
            set<Command> {
                add<CommandA>()
            }
            childFactory(::createChild)
            return createImpl()
        }
        
        @ChildFactory
        fun createChild(): ChildComponent {
            transient { CommandB() }
            set<Command> {
                add<CommandA>()
            }
            return createImpl()
        }
         """
    ) {
        assertInternalError("already bound")
    }

}