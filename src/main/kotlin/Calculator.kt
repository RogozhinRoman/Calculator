import java.math.BigInteger

fun main() {
    val calculator = Calculator()

    var isTerminated = false
    while (!isTerminated) {
        val input = readln().trim()
        when {
            input.startsWith("/") -> isTerminated = calculator.handleCommand(input)
            input.isBlank() -> continue
            else -> {
                val (error, value) = calculator.tryCalculate(input)
                if (error?.message != null) {
                    println(error.message)
                } else if (value != null) {
                    println(value)
                }
            }
        }
    }
}

class Calculator {
    companion object {
        private val SUPPORTED_VARIABLE_NAME_PATTERN = "[a-zA-Z]*".toRegex()
        private val SUPPORTED_SPEC_SYMBOLS = "(-|[+]|=|[(]|[)]|[*]|/|\\^).".toRegex()
        private val SUPPORTED_OPERATIONS = listOf("+", "-", "*", "/", "^")
    }

    private val variables: MutableMap<String, String> = mutableMapOf()

    fun handleCommand(input: String): Boolean {
        return when (input) {
            "/exit" -> {
                println("Bye!")
                true
            }
            "/help" -> {
                println("The program calculates the sum of numbers")
                false
            }
            else -> {
                println("Unknown command")
                false
            }
        }
    }

    fun tryCalculate(input: String): Result<String> = when {
        !input.contains(SUPPORTED_SPEC_SYMBOLS) -> handleVariableOperation(input)
        input.contains('=') -> handleAssignment(input)
        else -> calculate(input)
    }

    private fun handleAssignment(input: String): Result<String> {
        val parts = input.split("\\s*=\\s*".toRegex())
        fun isNotExistedVariable(term: String) = term.toBigIntegerOrNull() == null && !variables.containsKey(term)

        return when {
            parts.size != 2 -> Result(Errors.INVALID_ASSIGNMENT)
            !SUPPORTED_VARIABLE_NAME_PATTERN.matches(parts.first()) -> Result(Errors.INVALID_IDENTIFIER)
            isNotExistedVariable(parts.last()) -> Result(Errors.INVALID_ASSIGNMENT)
            else -> {
                variables[parts.first()] = parts.last()
                return Result()
            }
        }

    }

    private fun handleVariableOperation(input: String): Result<String> {
        return if (!input.matches(SUPPORTED_VARIABLE_NAME_PATTERN)) {
            Result(Errors.INVALID_IDENTIFIER)
        } else if (!variables.containsKey(input)) {
            Result(Errors.UNKNOWN_VARIABLE)
        } else {
            getVariableValueOrError(input)
        }
    }

    private fun getVariableValueOrError(input: String): Result<String> {
        var currentVariableName = input
        while (true) {
            return if (!variables.containsKey(currentVariableName)) {
                Result(Errors.UNKNOWN_VARIABLE)
            } else if (variables[currentVariableName]!!.toBigIntegerOrNull() == null) {
                currentVariableName = variables[currentVariableName]!!
                continue
            } else {
                Result(variables[currentVariableName]!!)
            }
        }
    }

    private fun calculate(input: String): Result<String> {
        var postfixNotation: List<String>
        try {
            postfixNotation = NotationConverter.tryConvertFromInfixToPostfix(input)
        } catch (exception: Exception) {
            return Result(Errors.INVALID_EXPRESSION)
        }

        return Result(calculatePostfix(postfixNotation))
    }

    private fun calculatePostfix(postfixNotation: List<String>): String {
        val stack = ArrayDeque<String>()
        for (operand in postfixNotation) {
            if (!SUPPORTED_OPERATIONS.contains(operand)) {
                stack.addFirst(operand)
            } else {
                val right = stack.removeFirst().getValue()
                val left = stack.removeFirst().getValue()

                stack.addFirst(calculate(left, right, operand).toString())
            }
        }
        return stack.single()
    }

    private fun calculate(left: BigInteger, right: BigInteger, operator: String): BigInteger {
        return when (operator) {
            "+" -> left + right
            "-" -> left - right
            "/" -> left / right
            "*" -> left * right
            "^" -> left.pow(right.toInt())
            else -> throw Exception("Unsupported operator")
        }
    }

    private fun String.getValue() = toBigIntegerOrNull() ?: BigInteger(getVariableValueOrError(this).value!!)
}

data class Result<T> private constructor(val error: Errors?, val value: T?) {
    constructor(error: Errors) : this(error, null)
    constructor(value: T) : this(null, value)
    constructor() : this(null, null)
}

enum class Errors(val message: String) {
    INVALID_EXPRESSION("Invalid expression"),
    INVALID_IDENTIFIER("Invalid identifier"),
    INVALID_ASSIGNMENT("Invalid assignment"),
    UNKNOWN_VARIABLE("Unknown variable"),
}

class NotationConverter {
    companion object {
        private val OPERATORS_WITH_PRIORITIES = mapOf(
            "+" to 1,
            "-" to 1,
            "*" to 2,
            "/" to 2,
            "^" to 3,
        )
        private val notationDelimiters = arrayOf(' ')
        private val parenthesis = arrayOf('(', ')')

        fun tryConvertFromInfixToPostfix(infixNotatedInput: String): List<String> {
            val stack = ArrayDeque<String>()
            var pointer = 0
            val result = mutableListOf<String>()

            while (pointer < infixNotatedInput.length) {
                if (infixNotatedInput[pointer].isWhitespace()) {
                    pointer++
                    continue
                }

                var operand = cutOperand(pointer, infixNotatedInput)
                pointer += operand.length

                if (OPERATORS_WITH_PRIORITIES.containsKey(operand.first().toString())) {
                    operand = if (operand.length > 1 && !arrayOf('+', '-').contains(operand.first())) {
                        throw Exception("Invalid expression")
                    } else if (operand.first() == '+') {
                        "+"
                    } else if (operand.first() == '-') {
                        if (operand.length % 2 == 0) "+" else "-"
                    } else {
                        operand
                    }
                }

                if (operand.first().isLetter() || operand.first().isDigit()) {
                    result.add(operand)
                } else if (stack.isEmpty() || stack.first() == "(") {
                    stack.addFirst(operand)
                } else if (OPERATORS_WITH_PRIORITIES.containsKey(operand)) {
                    if (OPERATORS_WITH_PRIORITIES[operand]!!
                            .compareTo(OPERATORS_WITH_PRIORITIES.getOrDefault(stack.first(), Int.MAX_VALUE)) == 1
                    ) {
                        stack.addFirst(operand)
                    } else {
                        while (stack.isNotEmpty()
                            && (stack.first() != "("
                                    || OPERATORS_WITH_PRIORITIES[operand]!!
                                .compareTo(OPERATORS_WITH_PRIORITIES.getOrDefault(stack.first(), Int.MAX_VALUE)) == 1)
                        ) {
                            result.add(stack.removeFirst())
                        }

                        stack.addFirst(operand)
                    }
                } else if (operand == "(") {
                    stack.addFirst(operand)
                } else if (operand == ")") {
                    while (stack.isNotEmpty() && (stack.first() != "(")) {
                        result.add(stack.removeFirst())
                    }
                    stack.removeFirst()
                }
            }

            while (stack.isNotEmpty()) {
                val topElement = stack.removeFirst()
                if (topElement == "(" || topElement == ")") {
                    throw Exception("Invalid expression")
                }
                result.add(topElement)
            }

            return result
        }

        private fun cutOperand(startIndex: Int, input: String): String {
            var endIndex = startIndex
            while (endIndex < input.length
                && !notationDelimiters.contains(input[endIndex])
                && !parenthesis.contains(input[endIndex])
            ) {
                endIndex++
            }

            return if (startIndex == endIndex) input[startIndex].toString() else input.substring(startIndex, endIndex)
        }
    }
}
