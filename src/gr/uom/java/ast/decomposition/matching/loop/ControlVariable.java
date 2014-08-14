package gr.uom.java.ast.decomposition.matching.loop;

import gr.uom.java.ast.util.ExpressionExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

@SuppressWarnings("unchecked")
public class ControlVariable extends AbstractControlVariable
{
	private SimpleName variableNode;

	public ControlVariable(SimpleName variableNode, Statement loopBody, List<Expression> forUpdaters)
	{
		this.variableNode     = variableNode;
		this.variableUpdaters = getVariableUpdaters(variableNode, loopBody, forUpdaters);
		if (variableUpdaters.size() > 0)
		{
			this.startValue                     = getStartValue(variableNode);
			ASTNode conditionContainingVariable = getConditionContainingVariable(variableNode);
			this.endValue                       = getEndValue(variableNode, conditionContainingVariable);
		}
	}

	public SimpleName getVariable()
	{
		return variableNode;
	}

	private static ASTNode getConditionContainingVariable(SimpleName variableNode)
	{
		ASTNode currentParent = variableNode;
		while (currentParent != null && currentParent.getParent() instanceof Expression)
		{
			currentParent = currentParent.getParent();
			ITypeBinding currentParentBinding = ((Expression)currentParent).resolveTypeBinding();
			if (currentParentBinding.getQualifiedName().equals("boolean"))
			{
				return currentParent;
			}
		}
		return currentParent;
	}

	// ****************************************************************************************************************************************************************
	// startValue methods
	// ****************************************************************************************************************************************************************
	
	private static VariableValue getStartValue(SimpleName variableNode)
	{
		VariableValue variableValue         = new VariableValue(VariableValue.ValueType.INTEGER);		// begin as an integer so, if at any point in the modifiers, there is a variable, the whole value becomes variable
		List<ASTNode> contributingModifiers = getValueContributingModifiers(variableNode);
		Iterator<ASTNode> it                = contributingModifiers.iterator();
		// we traverse the contributingModifiers and determine the type of value and, if possible, the cumulative value
		while (it.hasNext())
		{
			ASTNode currentNode          = it.next();
			Assignment.Operator operator = null;
			Expression rightHandSide     = null;
			Integer updateValue          = null;
			if (currentNode instanceof Assignment)
			{
				Assignment assignment = (Assignment) currentNode;
				operator              = assignment.getOperator();
				rightHandSide         = assignment.getRightHandSide();
			}
			// if the currentNode is a variable declaration or an ASSIGN Assignment
			if (currentNode instanceof VariableDeclaration || (operator != null && operator == Assignment.Operator.ASSIGN))
			{
				// take the rightHandSide of either
				Expression expression = null;
				if (currentNode instanceof VariableDeclaration)
				{
					VariableDeclaration variableDeclaration = (VariableDeclaration) currentNode;
					expression                              = variableDeclaration.getInitializer();
				}
				else if (currentNode instanceof Assignment)
				{
					expression = rightHandSide;
				}
				// evaluate the rightHandSide
				if (expression != null)
				{
					if (expression instanceof MethodInvocation)
					{
						MethodInvocation methodInvocation      = (MethodInvocation) expression;
						List<Expression> arguments             = methodInvocation.arguments();
						IMethodBinding methodInvocationBinding = methodInvocation.resolveMethodBinding().getMethodDeclaration();
						setMethodInvocationStartValue(expression, methodInvocationBinding, arguments, variableValue);
					}
					else if (expression instanceof ClassInstanceCreation)
					{
						ClassInstanceCreation classInstanceCreation = (ClassInstanceCreation) expression;
						List<Expression> arguments                  = classInstanceCreation.arguments();
						IMethodBinding methodInvocationBinding      = classInstanceCreation.resolveConstructorBinding().getMethodDeclaration();
						setMethodInvocationStartValue(expression, methodInvocationBinding, arguments, variableValue);
					}
					else if (expression instanceof QualifiedName)
					{
						if (ConditionalLoopUtilities.isLengthFieldAccess(expression))
						{
							variableValue.setType(VariableValue.ValueType.DATA_STRUCTURE_SIZE);
							variableValue.setValue(0);
						}
						else
						{
							variableValue.setType(VariableValue.ValueType.VARIABLE);
						}
					}
					else
					{
						variableValue.setValue(ConditionalLoopUtilities.getIntegerValue(expression));
						if (variableValue.getValue() == null)
						{
							variableValue.setType(VariableValue.ValueType.VARIABLE);
						}
					}
				}
			}
			// if the currentNode was an Assignment but not with an ASSIGN operator (the later assumed if it made it here), a Prefix or PostfixExpression, or a MethodInvocation
			else if (currentNode instanceof Assignment || currentNode instanceof PrefixExpression || currentNode instanceof PostfixExpression || currentNode instanceof MethodInvocation)
			{
				updateValue = ConditionalLoopUtilities.getUpdateValue((Expression)currentNode);
				if (variableValue.getValue() != null && updateValue != null)
				{
					variableValue.setValue(variableValue.getValue() + updateValue);
				}
				else
				{
					variableValue.setType(VariableValue.ValueType.VARIABLE);
				}
			}
		}
		return variableValue;
	}

	private static void setMethodInvocationStartValue(Expression expression, IMethodBinding methodInvocationBinding, List<Expression> arguments, VariableValue variableValue)
	{
		ConditionalLoopBindingInformation bindingInformation = ConditionalLoopBindingInformation.getInstance();
		String bindingKey = methodInvocationBinding.getMethodDeclaration().getKey();
		if (bindingInformation.iteratorInstantiationMethodBindingStartValuesContains(bindingKey))
		{
			Integer value = bindingInformation.getIteratorInstantiationMethodBindingStartValue(bindingKey);
			if (value == null)
			{
				if (arguments.size() == 1 && ConditionalLoopUtilities.isSizeInvocation(arguments.get(0)))
				{
					variableValue.setType(VariableValue.ValueType.DATA_STRUCTURE_SIZE);
					variableValue.setValue(0);
				}
				else
				{
					variableValue.setType(VariableValue.ValueType.VARIABLE);
				}
			}
			else
			{
				variableValue.setValue(value);
			}
		}
		else if (ConditionalLoopUtilities.isSizeInvocation(expression))
		{
			variableValue.setType(VariableValue.ValueType.DATA_STRUCTURE_SIZE);
			variableValue.setValue(0);
		}
		else
		{
			variableValue.setType(VariableValue.ValueType.VARIABLE);
		}
	}
	
	private static List<ASTNode> getValueContributingModifiers(SimpleName variable)
	{
		List<ASTNode> allVariableModifiers  = getAllVariableModifiersInParentMehtod(variable);
		List<ASTNode> contributingModifiers = null;
		boolean noModifierInLowerScope      = true;
		MethodDeclaration parentMethod      = ConditionalLoopUtilities.findParentMethodDeclaration(variable);
		// create a list of all parents of the specified variable until the root method
		List<ASTNode> variableParents = new ArrayList<ASTNode>();
		ASTNode currentVariableParent = variable.getParent();
		while (currentVariableParent != null && currentVariableParent != parentMethod)
		{
			variableParents.add(currentVariableParent);
			currentVariableParent = currentVariableParent.getParent();
		}
		variableParents.add(parentMethod);
		// we traverse allVariableModifiers and build a list of nodes that will influence the final value
		Iterator<ASTNode> it = allVariableModifiers.iterator();
		while (it.hasNext())
		{
			ASTNode currentNode = it.next();
			boolean currentNodeAdded = false;
			// if the current node is the declaration or an assignment, the list restarts the modifiers. if it is a plus, minus, times, or divide equals, then it adds to the modifiers
			if (currentNode instanceof VariableDeclaration)
			{
				contributingModifiers = new ArrayList<ASTNode>();
				contributingModifiers.add(currentNode);
				currentNodeAdded = true;
				noModifierInLowerScope = true;
			}
			else if (currentNode instanceof Assignment)
			{
				Assignment assignment = (Assignment) currentNode;
				Assignment.Operator operator = assignment.getOperator();
				if (operator == Assignment.Operator.ASSIGN)
				{
					contributingModifiers = new ArrayList<ASTNode>();
					contributingModifiers.add(currentNode);
					currentNodeAdded = true;
					noModifierInLowerScope = true;
				}
				else if (operator == Assignment.Operator.PLUS_ASSIGN ||
						operator == Assignment.Operator.MINUS_ASSIGN ||
						operator == Assignment.Operator.TIMES_ASSIGN ||
						operator == Assignment.Operator.DIVIDE_ASSIGN)
				{
					contributingModifiers.add(currentNode);
					currentNodeAdded = true;
				}				
			}
			else if (currentNode instanceof PrefixExpression || currentNode instanceof PostfixExpression)
			{
				contributingModifiers.add(currentNode);
				currentNodeAdded = true;
			}
			else if (currentNode instanceof MethodInvocation)
			{
				MethodInvocation currentMethodInvocation = (MethodInvocation) currentNode;
				ConditionalLoopBindingInformation bindingInformation = ConditionalLoopBindingInformation.getInstance();
				String currentMethodBindingKey = currentMethodInvocation.resolveMethodBinding().getMethodDeclaration().getKey();
				if (bindingInformation.updateMethodValuesContains(currentMethodBindingKey))
				{
					contributingModifiers.add(currentNode);
					currentNodeAdded = true;
				}
			}
			// if currentNode was added, move up through it's parents until the first block or conditional parent and check if it is in the variableParents list, if not, it is in a lower scope
			if (currentNodeAdded)
			{
				ASTNode currentNodeParent = currentNode.getParent();
				while (currentNodeParent != null)
				{
					if ((currentNodeParent instanceof MethodDeclaration || currentNodeParent instanceof IfStatement || currentNodeParent instanceof ForStatement ||
							currentNodeParent instanceof WhileStatement || currentNodeParent instanceof DoStatement || currentNodeParent instanceof EnhancedForStatement ||
							currentNodeParent instanceof SwitchStatement || currentNodeParent instanceof TryStatement))
					{
						if (!variableParents.contains(currentNodeParent))
						{
							noModifierInLowerScope = false;
						}
						break;
					}
					currentNodeParent = currentNodeParent.getParent();
				}
			}
		}
		// return constructed list if all modifiers are in same or higher scope
		if (noModifierInLowerScope)
		{
			return contributingModifiers;
		}
		return new ArrayList<ASTNode>();
	}
	
	// returns all modifiers of the specified variable occurring before it in its containing method
	private static List<ASTNode> getAllVariableModifiersInParentMehtod(SimpleName variable)
	{
		List<ASTNode> bodyVariableModifiers = null;
		MethodDeclaration parentMethod = ConditionalLoopUtilities.findParentMethodDeclaration(variable);
		if (parentMethod != null)
		{
			Block parentMethodBody = parentMethod.getBody();
			if (parentMethodBody != null)
			{
				ExpressionExtractor expressionExtractor = new ExpressionExtractor();
				bodyVariableModifiers = new ArrayList<ASTNode>(expressionExtractor.getVariableModifiers(parentMethodBody));
				// remove all variable updaters that are not modifying the specified variable or are after the position of the variable in use
				Iterator<ASTNode> it = bodyVariableModifiers.iterator();
				while (it.hasNext())
				{
					ASTNode currentNode = it.next();
					if (currentNode instanceof Expression)
					{
						Expression currentExpression = (Expression) currentNode;
						if (!ConditionalLoopUtilities.isUpdatingVariable(currentExpression, variable) || currentExpression.getStartPosition() >= variable.getStartPosition())
						{
							it.remove();
						}
					}
				}
				// add the variable's declaration
				VariableDeclaration variableDeclaration = ConditionalLoopUtilities.getVariableDeclaration(variable);
				if (variableDeclaration != null)
				{
					bodyVariableModifiers.add(0, variableDeclaration);
				}
			}
		}
		return bodyVariableModifiers;
	}

	// ****************************************************************************************************************************************************************
	// endValue methods
	// ****************************************************************************************************************************************************************

	private static VariableValue getEndValue(SimpleName variableNode, ASTNode conditionContainingVariable)
	{
		VariableValue variableValue = new VariableValue();
		if (conditionContainingVariable instanceof InfixExpression)
		{
			InfixExpression infixExpression = (InfixExpression) conditionContainingVariable;
			Expression leftOperand          = infixExpression.getLeftOperand();
			Expression rightOperand         = infixExpression.getRightOperand();
			// get the operand opposite to the variable
			Boolean isVariableLeftOperand   = ConditionalLoopUtilities.isVariableLeftOperand(variableNode, infixExpression);
			if (isVariableLeftOperand == null)
			{
				return null;
			}
			Expression nonVariableOperand = (isVariableLeftOperand) ? rightOperand : leftOperand;
			// evaluate the value of the opposing operand
			if (ConditionalLoopUtilities.isLengthFieldAccess(nonVariableOperand) || ConditionalLoopUtilities.isSizeInvocation(nonVariableOperand))
			{
				variableValue = new VariableValue(VariableValue.ValueType.DATA_STRUCTURE_SIZE);
			}
			else
			{
				Integer value = ConditionalLoopUtilities.getIntegerValue(nonVariableOperand);
				if (value != null)
				{
					variableValue = new VariableValue(value);
				}
			}
		}
		else if (conditionContainingVariable instanceof MethodInvocation)
		{
			// get the variableValue of that MethodBinding from ConditionalLoopBindingInformation
			MethodInvocation methodInvocation = (MethodInvocation) conditionContainingVariable;
			String methodBindingKey = methodInvocation.resolveMethodBinding().getMethodDeclaration().getKey();		// use .getMethodDeclaration() so we get the abstract method and not the method of the specific collection
			ConditionalLoopBindingInformation bindingInformation = ConditionalLoopBindingInformation.getInstance();
			if (bindingInformation.conditionalMethodBindingEndValuesContains(methodBindingKey))
			{
				variableValue = bindingInformation.getConditionalMethodBindingEndValue(methodBindingKey);
			}
		}
		return variableValue;
	}

	// ****************************************************************************************************************************************************************
	// updater methods
	// ****************************************************************************************************************************************************************
	
	private static List<VariableUpdater> getVariableUpdaters(SimpleName variable, Statement loopBody, List<Expression> forUpdaters)
	{
		List<VariableUpdater> variableUpdaters  = new ArrayList<VariableUpdater>();
		List<Expression> possibleUpdatersInBody = getAllFirstLevelUpdaters(loopBody);
		
		// find all updaters of the specified variable
		IBinding variableBinding = variable.resolveBinding();
		if (variableBinding != null && variableBinding.getKind() == IBinding.VARIABLE)
		{
			for (Expression currentUpdaterNode : forUpdaters)
			{
				if (ConditionalLoopUtilities.isUpdatingVariable(currentUpdaterNode, variable))
				{
					variableUpdaters.add(new VariableUpdater(currentUpdaterNode));
				}
			}
			for (Expression currentUpdaterNode : possibleUpdatersInBody)
			{
				if (ConditionalLoopUtilities.isUpdatingVariable(currentUpdaterNode, variable))
				{
					variableUpdaters.add(new VariableUpdater(currentUpdaterNode));
				}
			}
		}
		return variableUpdaters;
	}
	
	private static List<Expression> getAllFirstLevelUpdaters(Statement statement)
	{
		List<Expression> updaters               = new ArrayList<Expression>();
		ExpressionExtractor expressionExtractor = new ExpressionExtractor();
		List<Statement> innerStatements         = new ArrayList<Statement>();
		if (statement instanceof Block)
		{
			Block statementBlock = (Block) statement;
			innerStatements.addAll(statementBlock.statements());
		}
		else
		{
			innerStatements.add(statement);
		}
		// get all first level PrefixExpressions, PostfixExpressions, Assignments, and next() MethodInvocations from each inner statement
		for (Statement currentStatement : innerStatements)
		{
			// only updaters in an ExpressionStatment or VariableDeclaration are first level, unless a ConditionalExpression (handled in return statement)
			if (currentStatement instanceof ExpressionStatement || currentStatement instanceof VariableDeclarationStatement)
			{
				updaters.addAll(expressionExtractor.getPrefixExpressions(currentStatement));
				updaters.addAll(expressionExtractor.getPostfixExpressions(currentStatement));
				updaters.addAll(expressionExtractor.getAssignments(currentStatement));
				
				List<Expression> methodInvocations = expressionExtractor.getMethodInvocations(currentStatement);
				for (Expression currentExpression : methodInvocations)
				{
					if (currentExpression instanceof MethodInvocation)
					{
						MethodInvocation currentMethodInvocation      = (MethodInvocation) currentExpression;
						IMethodBinding currentMethodInvocationBinding = currentMethodInvocation.resolveMethodBinding();
						ConditionalLoopBindingInformation bindingInformation = ConditionalLoopBindingInformation.getInstance();
						if (bindingInformation.updateMethodValuesContains(currentMethodInvocationBinding.getMethodDeclaration().getKey()))
						{
							updaters.add(currentMethodInvocation);
						}
					}
				}
			}
		}
		return removeExpressionsInAConditionalExpression(updaters, statement);
	}
	
	private static List<Expression> removeExpressionsInAConditionalExpression(List<Expression> expressions, Statement containingStatement)
	{
		// remove any expressions in a ConditionalExpression
		ListIterator<Expression> it = expressions.listIterator();
		while (it.hasNext())
		{
			Expression currentUpdater = it.next();
			ASTNode parent = currentUpdater.getParent();
			while (parent != null && !parent.equals(containingStatement))
			{
				if (parent instanceof ConditionalExpression)
				{
					it.remove();
					break;
				}
				parent = parent.getParent();
			}
		}
		return expressions;
	}
}
