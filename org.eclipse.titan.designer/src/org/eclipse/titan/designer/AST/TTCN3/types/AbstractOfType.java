/******************************************************************************
 * Copyright (c) 2000-2018 Ericsson Telecom AB
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 ******************************************************************************/
package org.eclipse.titan.designer.AST.TTCN3.types;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.text.templates.Template;
import org.eclipse.titan.common.logging.ErrorReporter;
import org.eclipse.titan.designer.AST.ASTVisitor;
import org.eclipse.titan.designer.AST.ArraySubReference;
import org.eclipse.titan.designer.AST.Assignment;
import org.eclipse.titan.designer.AST.FieldSubReference;
import org.eclipse.titan.designer.AST.INamedNode;
import org.eclipse.titan.designer.AST.IReferenceChain;
import org.eclipse.titan.designer.AST.ISubReference;
import org.eclipse.titan.designer.AST.ISubReference.Subreference_type;
import org.eclipse.titan.designer.AST.IType;
import org.eclipse.titan.designer.AST.IValue;
import org.eclipse.titan.designer.AST.IValue.Value_type;
import org.eclipse.titan.designer.AST.Module;
import org.eclipse.titan.designer.AST.ParameterisedSubReference;
import org.eclipse.titan.designer.AST.Reference;
import org.eclipse.titan.designer.AST.ReferenceChain;
import org.eclipse.titan.designer.AST.ReferenceFinder;
import org.eclipse.titan.designer.AST.ReferenceFinder.Hit;
import org.eclipse.titan.designer.AST.Scope;
import org.eclipse.titan.designer.AST.Value;
import org.eclipse.titan.designer.AST.ASN1.ASN1Type;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Sequence_Type;
import org.eclipse.titan.designer.AST.ASN1.types.ASN1_Set_Type;
import org.eclipse.titan.designer.AST.TTCN3.Expected_Value_type;
import org.eclipse.titan.designer.AST.TTCN3.IIncrementallyUpdateable;
import org.eclipse.titan.designer.AST.TTCN3.attributes.RawAST;
import org.eclipse.titan.designer.AST.TTCN3.templates.SingleLenghtRestriction;
import org.eclipse.titan.designer.AST.TTCN3.types.subtypes.Length_ParsedSubType;
import org.eclipse.titan.designer.AST.TTCN3.types.subtypes.ParsedSubType;
import org.eclipse.titan.designer.AST.TTCN3.types.subtypes.SubType;
import org.eclipse.titan.designer.AST.TTCN3.values.ArrayDimension;
import org.eclipse.titan.designer.AST.TTCN3.values.Expression_Value.Operation_type;
import org.eclipse.titan.designer.AST.TTCN3.values.Integer_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.SetOf_Value;
import org.eclipse.titan.designer.AST.TTCN3.values.expressions.ExpressionStruct;
import org.eclipse.titan.designer.compiler.JavaGenData;
import org.eclipse.titan.designer.editors.ProposalCollector;
import org.eclipse.titan.designer.editors.actions.DeclarationCollector;
import org.eclipse.titan.designer.editors.ttcn3editor.TTCN3CodeSkeletons;
import org.eclipse.titan.designer.parsers.CompilationTimeStamp;
import org.eclipse.titan.designer.parsers.ttcn3parser.ReParseException;
import org.eclipse.titan.designer.parsers.ttcn3parser.TTCN3ReparseUpdater;

/**
 * @author Kristof Szabados
 * */
public abstract class AbstractOfType extends ASN1Type {

	public static final String INCOMPLETEPRESENTERROR = "Not used symbol `-' is not allowed in this context";

	private static final String FULLNAMEPART = ".oftype";

	private final IType ofType;
	private boolean componentInternal;

	public AbstractOfType(final IType ofType) {
		this.ofType = ofType;

		if (ofType != null) {
			ofType.setOwnertype(TypeOwner_type.OT_RECORD_OF, this);
			ofType.setFullNameParent(this);
		}
		componentInternal = false;
	}

	public IType getOfType() {
		return ofType;
	}

	@Override
	/** {@inheritDoc} */
	public StringBuilder getFullName(final INamedNode child) {
		final StringBuilder builder = super.getFullName(child);

		if (ofType == child) {
			return builder.append(FULLNAMEPART);
		}

		return builder;
	}

	@Override
	/** {@inheritDoc} */
	public void setMyScope(final Scope scope) {
		super.setMyScope(scope);
		if (ofType != null) {
			ofType.setMyScope(scope);
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean isIdentical(final CompilationTimeStamp timestamp, final IType type) {
		check(timestamp);
		type.check(timestamp);
		final IType temp = type.getTypeRefdLast(timestamp);

		if (getIsErroneous(timestamp) || temp.getIsErroneous(timestamp)) {
			return true;
		}

		return this == temp;
	}

	/**
	 * Checks that the provided type is sub-type compatible with the actual
	 * set of type.
	 * <p>
	 * In case of sequence/set/array this means that the number of their
	 * fields fulfills the length restriction of the set of type.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle
	 * @param other
	 *                the type to check against.
	 *
	 * @return true if they are sub-type compatible, false otherwise.
	 * */
	public boolean isSubtypeCompatible(final CompilationTimeStamp timestamp, final IType other) {
		if (subType == null || other == null) {
			return true;
		}

		long nofComponents;
		switch (other.getTypetype()) {
		case TYPE_ASN1_SEQUENCE:
			nofComponents = ((ASN1_Sequence_Type) other).getNofComponents(timestamp);
			break;
		case TYPE_TTCN3_SEQUENCE:
			nofComponents = ((TTCN3_Sequence_Type) other).getNofComponents();
			break;
		case TYPE_ASN1_SET:
			nofComponents = ((ASN1_Set_Type) other).getNofComponents(timestamp);
			break;
		case TYPE_TTCN3_SET:
			nofComponents = ((TTCN3_Set_Type) other).getNofComponents();
			break;
		case TYPE_SEQUENCE_OF:
		case TYPE_SET_OF:
			if (other.getSubtype() == null) {
				return true;
			}

			return subType.isCompatible(timestamp, other.getSubtype());
		case TYPE_ARRAY: {
			final ArrayDimension dimension = ((Array_Type) other).getDimension();
			if (dimension.getIsErroneous(timestamp)) {
				return false;
			}

			nofComponents = dimension.getSize();
			break;
		}
		default:
			return false;
		}

		final List<ParsedSubType> tempRestrictions = new ArrayList<ParsedSubType>(1);
		final Integer_Value length = new Integer_Value(nofComponents);
		tempRestrictions.add(new Length_ParsedSubType(new SingleLenghtRestriction(length)));
		final SubType tempSubtype = new SubType(getSubtypeType(), this, tempRestrictions, null);
		tempSubtype.check(timestamp);
		return subType.isCompatible(timestamp, tempSubtype);
	}

	@Override
	/** {@inheritDoc} */
	public Type_type getTypetypeTtcn3() {
		if (isErroneous) {
			return Type_type.TYPE_UNDEFINED;
		}

		return getTypetype();
	}

	@Override
	/** {@inheritDoc} */
	public String getTypename() {
		return getFullName();
	}

	@Override
	/** {@inheritDoc} */
	public boolean isComponentInternal(final CompilationTimeStamp timestamp) {
		check(timestamp);

		return componentInternal;
	}

	@Override
	/** {@inheritDoc} */
	public void check(final CompilationTimeStamp timestamp) {
		if (lastTimeChecked != null && !lastTimeChecked.isLess(timestamp)) {
			return;
		}

		lastTimeChecked = timestamp;
		if (myScope != null) {
			final Module module = myScope.getModuleScope();
			if (module != null && module.getSkippedFromSemanticChecking()) {
				lastTimeChecked = timestamp;
				return;
			}
		}
		componentInternal = false;
		isErroneous = false;

		initAttributes(timestamp);

		if (ofType == null) {
			setIsErroneous(true);
		} else {
			ofType.setGenName(getGenNameOwn(), "0");
			ofType.setParentType(this);
			ofType.check(timestamp);
			if (!isAsn()) {
				ofType.checkEmbedded(timestamp, ofType.getLocation(), true, "embedded into another type");
			}
			componentInternal = ofType.isComponentInternal(timestamp);
		}

		if (constraints != null) {
			constraints.check(timestamp);
		}

		checkSubtypeRestrictions(timestamp);

		if (myScope != null) {
			checkEncode(timestamp);
			checkVariants(timestamp);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void checkComponentInternal(final CompilationTimeStamp timestamp, final Set<IType> typeSet, final String operation) {
		if (typeSet.contains(this)) {
			return;
		}

		if (ofType != null && ofType.isComponentInternal(timestamp)) {
			typeSet.add(this);
			ofType.checkComponentInternal(timestamp, typeSet, operation);
			typeSet.remove(this);
		}
	}

	/**
	 * Checks the SequenceOf_value kind value against this type.
	 * SequenceOf_value kinds have to be converted before calling this
	 * function.
	 * <p>
	 * Please note, that this function can only be called once we know for
	 * sure that the value is of set-of type.
	 *
	 * @param timestamp
	 *                the timestamp of the actual semantic check cycle.
	 * @param value
	 *                the value to be checked
	 * @param expectedValue
	 *                the kind of value expected here.
	 * @param incompleteAllowed
	 *                wheather incomplete value is allowed or not.
	 * @param implicitOmit
	 *                true if the implicit omit optional attribute was set
	 *                for the value, false otherwise
	 * */
	public boolean checkThisValueSetOf(final CompilationTimeStamp timestamp, final SetOf_Value value, final Assignment lhs, final Expected_Value_type expectedValue,
			final boolean incompleteAllowed, final boolean implicitOmit, final boolean strElem) {
		boolean selfReference = false;

		if (value.isIndexed()) {
			boolean checkHoles = Expected_Value_type.EXPECTED_CONSTANT.equals(expectedValue);
			BigInteger maxIndex = BigInteger.valueOf(-1);
			final Map<BigInteger, Integer> indexMap = new HashMap<BigInteger, Integer>(value.getNofComponents());
			for (int i = 0, size = value.getNofComponents(); i < size; i++) {
				final IValue component = value.getValueByIndex(i);
				final Value index = value.getIndexByIndex(i);
				final IReferenceChain referenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
				final IValue indexLast = index.getValueRefdLast(timestamp, referenceChain);
				referenceChain.release();

				if (indexLast.getIsErroneous(timestamp) || !Value_type.INTEGER_VALUE.equals(indexLast.getValuetype())) {
					checkHoles = false;
				} else {
					final BigInteger tempIndex = ((Integer_Value) indexLast).getValueValue();
					if (tempIndex.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 1) {
						index.getLocation()
						.reportSemanticError(
								MessageFormat.format(
										"A integer value less than `{0}'' was expected for indexing type `{1}'' instead of `{2}''",
										Integer.MAX_VALUE, getTypename(), tempIndex));
						checkHoles = false;
					} else if (tempIndex.compareTo(BigInteger.ZERO) == -1) {
						index.getLocation().reportSemanticError(MessageFormat.format(
								"A non-negative integer value was expected for indexing type `{0}'' instead of `{1}''", getTypename(), tempIndex));
						checkHoles = false;
					} else if (indexMap.containsKey(tempIndex)) {
						index.getLocation().reportSemanticError(
								MessageFormat.format("Duplicate index value `{0}'' for components {1} and {2}",
										tempIndex, indexMap.get(tempIndex), i + 1));
						checkHoles = false;
					} else {
						indexMap.put(tempIndex, Integer.valueOf(i + 1));
						if (maxIndex.compareTo(tempIndex) == -1) {
							maxIndex = tempIndex;
						}
					}
				}

				component.setMyGovernor(getOfType());
				final IValue tempValue2 = getOfType().checkThisValueRef(timestamp, component);
				selfReference |= getOfType().checkThisValue(timestamp, tempValue2, lhs,
						new ValueCheckingOptions(expectedValue, incompleteAllowed, false, true, implicitOmit, strElem));
			}
			if (checkHoles && maxIndex.compareTo(BigInteger.valueOf(indexMap.size() - 1)) != 0) {
				value.getLocation().reportSemanticError("It's not allowed to create hole(s) in constant values");
			}
		} else {
			for (int i = 0, size = value.getNofComponents(); i < size; i++) {
				final IValue component = value.getValueByIndex(i);
				component.setMyGovernor(getOfType());
				if (Value_type.NOTUSED_VALUE.equals(component.getValuetype())) {
					if (!incompleteAllowed) {
						component.getLocation().reportSemanticError(INCOMPLETEPRESENTERROR);
					}
				} else {
					final IValue tempValue2 = getOfType().checkThisValueRef(timestamp, component);
					selfReference |= getOfType().checkThisValue(timestamp, tempValue2, lhs,
							new ValueCheckingOptions(expectedValue, incompleteAllowed, false, true, implicitOmit, strElem));
				}
			}
		}

		value.setLastTimeChecked(timestamp);

		return selfReference;
	}

	@Override
	/** {@inheritDoc} */
	public boolean canHaveCoding(final CompilationTimeStamp timestamp, final MessageEncoding_type coding, final IReferenceChain refChain) {
		if (refChain.contains(this)) {
			return true;
		}
		refChain.add(this);

		for (int i = 0; i < codingTable.size(); i++) {
			final Coding_Type tempCodingType = codingTable.get(i);

			if (tempCodingType.builtIn && tempCodingType.builtInCoding.equals(coding)) {
				return true; // coding already added
			}
		}

		refChain.markState();

		final boolean result = ofType.getTypeRefdLast(timestamp).canHaveCoding(timestamp, coding, refChain);
		refChain.previousState();

		return result;
	}

	@Override
	/** {@inheritDoc} */
	public void setGenerateCoderFunctions(final CompilationTimeStamp timestamp, final MessageEncoding_type encodingType) {
		switch(encodingType) {
		case RAW:
			break;
		default:
			return;
		}

		if (getGenerateCoderFunctions(encodingType)) {
			//already set
			return;
		}

		codersToGenerate.add(encodingType);
		getOfType().getTypeRefdLast(timestamp).setGenerateCoderFunctions(timestamp, encodingType);
	}

	@Override
	/** {@inheritDoc} */
	public void checkCodingAttributes(final CompilationTimeStamp timestamp, final IReferenceChain refChain) {
		//check raw attributes
		if (subType != null) {
			final int restrictionLength = subType.get_length_restriction();
			if (restrictionLength != -1) {
				if (rawAttribute == null) {
					rawAttribute = new RawAST(getDefaultRawFieldLength());
				}
				rawAttribute.length_restriction = restrictionLength;

				ofType.forceRaw(timestamp);
				if (rawAttribute.fieldlength == 0 && rawAttribute.length_restriction != -1) {
					rawAttribute.fieldlength = rawAttribute.length_restriction;
					rawAttribute.length_restriction = -1;
				}
				if (rawAttribute.length_restriction != -1 && rawAttribute.length_restriction != rawAttribute.fieldlength) {
					getLocation().reportSemanticError(MessageFormat.format("Invalid length specified in parameter FIELDLENGTH for type `{0}''. The FIELDLENGTH must be equal to specified length restriction", getFullName()));
				}
			}
		}
		//TODO add checks for other encodings.

		if (refChain.contains(this)) {
			return;
		}

		refChain.add(this);
		refChain.markState();

		ofType.checkCodingAttributes(timestamp, refChain);

		refChain.previousState();
	}

	@Override
	/** {@inheritDoc} */
	public void getTypesWithNoCodingTable(final CompilationTimeStamp timestamp, final ArrayList<IType> typeList, final boolean onlyOwnTable) {
		if (typeList.contains(this)) {
			return;
		}

		if ((onlyOwnTable && codingTable.isEmpty()) || (!onlyOwnTable && getTypeWithCodingTable(timestamp, false) == null)) {
			typeList.add(this);
		}

		ofType.getTypesWithNoCodingTable(timestamp, typeList, onlyOwnTable);
	}

	@Override
	/** {@inheritDoc} */
	public boolean getSubrefsAsArray(final CompilationTimeStamp timestamp, final Reference reference, final int actualSubReference,
			final List<Integer> subrefsArray, final List<IType> typeArray) {
		final List<ISubReference> subreferences = reference.getSubreferences();
		if (subreferences.size() <= actualSubReference) {
			return true;
		}

		final ISubReference subreference = subreferences.get(actualSubReference);
		if (subreference.getReferenceType() != Subreference_type.arraySubReference) {
			ErrorReporter.INTERNAL_ERROR();
			return false;
		}

		final Value indexValue = ((ArraySubReference) subreference).getValue();
		if (indexValue == null) {
			ErrorReporter.INTERNAL_ERROR();
			return false;
		}

		final IValue last = indexValue.getValueRefdLast(timestamp, Expected_Value_type.EXPECTED_CONSTANT, null);
		if (last == null) {
			ErrorReporter.INTERNAL_ERROR();
			return false;
		}
		if (last.getExpressionReturntype(timestamp, Expected_Value_type.EXPECTED_CONSTANT) != Type_type.TYPE_INTEGER) {
			return false;
		}
		if (!Value_type.INTEGER_VALUE.equals(last.getValuetype())) {
			return false;
		}

		final Integer_Value lastInteger = (Integer_Value) last;
		if (lastInteger.isNative()) {
			final int fieldIndex = (int) lastInteger.getValue();
			if (fieldIndex < 0) {
				return false;
			}
			subrefsArray.add(fieldIndex);
			typeArray.add(this);
		} else {
			return false;
		}
		if (ofType == null) {
			ErrorReporter.INTERNAL_ERROR();
			return false;
		}
		return ofType.getSubrefsAsArray(timestamp, reference, actualSubReference + 1, subrefsArray, typeArray);
	}

	@Override
	/** {@inheritDoc} */
	public IType getFieldType(final CompilationTimeStamp timestamp, final Reference reference, final int actualSubReference,
			final Expected_Value_type expectedIndex, final IReferenceChain refChain, final boolean interruptIfOptional) {
		final List<ISubReference> subreferences = reference.getSubreferences();
		if (subreferences.size() <= actualSubReference) {
			return this;
		}

		final Expected_Value_type internalExpectation = expectedIndex == Expected_Value_type.EXPECTED_TEMPLATE ? Expected_Value_type.EXPECTED_DYNAMIC_VALUE
				: expectedIndex;
		final ISubReference subreference = subreferences.get(actualSubReference);
		switch (subreference.getReferenceType()) {
		case arraySubReference:
			final Value indexValue = ((ArraySubReference) subreference).getValue();
			if (indexValue != null) {
				indexValue.setLoweridToReference(timestamp);
				final Type_type tempType = indexValue.getExpressionReturntype(timestamp, expectedIndex);

				switch (tempType) {
				case TYPE_INTEGER:
					final IValue last = indexValue.getValueRefdLast(timestamp, expectedIndex, refChain);
					if (Value_type.INTEGER_VALUE.equals(last.getValuetype())) {
						final Integer_Value lastInteger = (Integer_Value) last;
						if (lastInteger.isNative()) {
							final long temp = lastInteger.getValue();
							if (temp < 0) {
								indexValue.getLocation().reportSemanticError(
										MessageFormat.format(SequenceOf_Type.NONNEGATIVINDEXEXPECTED, temp));
								indexValue.setIsErroneous(true);
							}
						} else {
							indexValue.getLocation().reportSemanticError(
									MessageFormat.format(SequenceOf_Type.TOOBIGINDEX, lastInteger.getValueValue(), getTypename()));
							indexValue.setIsErroneous(true);
						}
					}
					break;
				case TYPE_UNDEFINED:
					indexValue.setIsErroneous(true);
					break;
				default:
					indexValue.getLocation().reportSemanticError(SequenceOf_Type.INTEGERINDEXEXPECTED);
					indexValue.setIsErroneous(true);
					break;
				}
			}

			if (getOfType() != null) {
				return getOfType().getFieldType(timestamp, reference, actualSubReference + 1, internalExpectation, refChain,
						interruptIfOptional);
			}

			return null;
		case fieldSubReference:
			subreference.getLocation().reportSemanticError(
					MessageFormat.format(FieldSubReference.INVALIDSUBREFERENCE, ((FieldSubReference) subreference).getId()
							.getDisplayName(), getTypename()));
			return null;
		case parameterisedSubReference:
			subreference.getLocation().reportSemanticError(
					MessageFormat.format(FieldSubReference.INVALIDSUBREFERENCE, ((ParameterisedSubReference) subreference)
							.getId().getDisplayName(), getTypename()));
			return null;
		default:
			subreference.getLocation().reportSemanticError(ISubReference.INVALIDSUBREFERENCE);
			return null;
		}
	}

	@Override
	/** {@inheritDoc} */
	public boolean getFieldTypesAsArray(final Reference reference, final int actualSubReference, final List<IType> typeArray) {
		final List<ISubReference> subreferences = reference.getSubreferences();
		if (subreferences.size() <= actualSubReference) {
			return true;
		}

		final ISubReference subreference = subreferences.get(actualSubReference);
		if (subreference.getReferenceType() != Subreference_type.arraySubReference) {
			return false;
		}
		typeArray.add(this);
		if (ofType == null) {
			return false;
		}
		return ofType.getFieldTypesAsArray(reference, actualSubReference + 1, typeArray);
	}

	@Override
	/** {@inheritDoc} */
	public void addProposal(final ProposalCollector propCollector, final int i) {
		final List<ISubReference> subreferences = propCollector.getReference().getSubreferences();
		if (subreferences.size() < i) {
			return;
		} else if (subreferences.size() == i) {
			final ISubReference subreference = subreferences.get(i - 1);
			if (Subreference_type.fieldSubReference.equals(subreference.getReferenceType())) {
				final String candidate = ((FieldSubReference) subreference).getId().getDisplayName();
				propCollector.addTemplateProposal(candidate, new Template(candidate + "[index]", candidate + " with index",
						propCollector.getContextIdentifier(), candidate + "[${index}]", false),
						TTCN3CodeSkeletons.SKELETON_IMAGE);
			}
			return;
		}

		final ISubReference subreference = subreferences.get(i);
		if (Subreference_type.arraySubReference.equals(subreference.getReferenceType()) && subreferences.size() > i + 1 && ofType != null) {
			ofType.addProposal(propCollector, i + 1);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void addDeclaration(final DeclarationCollector declarationCollector, final int i) {
		final List<ISubReference> subreferences = declarationCollector.getReference().getSubreferences();
		if (subreferences.size() <= i) {
			return;
		}

		final ISubReference subreference = subreferences.get(i);
		if (Subreference_type.arraySubReference.equals(subreference.getReferenceType()) && subreferences.size() > i + 1 && ofType != null) {
			ofType.addDeclaration(declarationCollector, i + 1);
		}
	}

	@Override
	/** {@inheritDoc} */
	public void updateSyntax(final TTCN3ReparseUpdater reparser, final boolean isDamaged) throws ReParseException {
		if (isDamaged) {
			lastTimeChecked = null;
			boolean handled = false;

			if (ofType instanceof IIncrementallyUpdateable && reparser.envelopsDamage(ofType.getLocation())) {
				((IIncrementallyUpdateable) ofType).updateSyntax(reparser, true);
				reparser.updateLocation(ofType.getLocation());
				handled = true;
			}

			if (subType != null) {
				subType.updateSyntax(reparser, false);
				handled = true;
			}

			if (handled) {
				return;
			}

			throw new ReParseException();
		}

		if (ofType instanceof IIncrementallyUpdateable) {
			((IIncrementallyUpdateable) ofType).updateSyntax(reparser, false);
			reparser.updateLocation(ofType.getLocation());
		} else if (ofType != null) {
			throw new ReParseException();
		}

		if (subType != null) {
			subType.updateSyntax(reparser, false);
		}

		if (withAttributesPath != null) {
			withAttributesPath.updateSyntax(reparser, false);
			reparser.updateLocation(withAttributesPath.getLocation());
		}
	}

	@Override
	/** {@inheritDoc} */
	public void getEnclosingField(final int offset, final ReferenceFinder rf) {
		if (ofType == null) {
			return;
		}

		ofType.getEnclosingField(offset, rf);
	}

	@Override
	/** {@inheritDoc} */
	public void findReferences(final ReferenceFinder referenceFinder, final List<Hit> foundIdentifiers) {
		super.findReferences(referenceFinder, foundIdentifiers);
		if (ofType != null) {
			ofType.findReferences(referenceFinder, foundIdentifiers);
		}
	}

	@Override
	/** {@inheritDoc} */
	protected boolean memberAccept(final ASTVisitor v) {
		if (!super.memberAccept(v)) {
			return false;
		}
		if (ofType != null && !ofType.accept(v)) {
			return false;
		}
		return true;
	}

	@Override
	/** {@inheritDoc} */
	public void generateCodeIsPresentBoundChosen(final JavaGenData aData, final ExpressionStruct expression, final List<ISubReference> subreferences,
			final int subReferenceIndex, final String globalId, final String externalId, final boolean isTemplate, final Operation_type optype, final String field) {
		if (subreferences == null || getIsErroneous(CompilationTimeStamp.getBaseTimestamp())) {
			return;
		}

		if (subReferenceIndex >= subreferences.size()) {
			return;
		}

		final StringBuilder closingBrackets = new StringBuilder();
		if(isTemplate) {
			boolean anyvalueReturnValue = true;
			if (optype == Operation_type.ISPRESENT_OPERATION) {
				anyvalueReturnValue = isPresentAnyvalueEmbeddedField(expression, subreferences, subReferenceIndex);
			} else if (optype == Operation_type.ISCHOOSEN_OPERATION) {
				anyvalueReturnValue = false;
			}

			expression.expression.append(MessageFormat.format("if({0}) '{'\n", globalId));
			expression.expression.append(MessageFormat.format("switch({0}.get_selection()) '{'\n", externalId));
			expression.expression.append("case UNINITIALIZED_TEMPLATE:\n");
			expression.expression.append(MessageFormat.format("{0} = false;\n", globalId));
			expression.expression.append("break;\n");
			expression.expression.append("case ANY_VALUE:\n");
			expression.expression.append(MessageFormat.format("{0} = {1};\n", globalId, anyvalueReturnValue?"true":"false"));
			expression.expression.append("break;\n");
			expression.expression.append("case SPECIFIC_VALUE:{\n");

			closingBrackets.append("break;}\n");
			closingBrackets.append("default:\n");
			closingBrackets.append(MessageFormat.format("{0} = false;\n", globalId));
			closingBrackets.append("break;\n");
			closingBrackets.append("}\n");
			closingBrackets.append("}\n");
		}

		final ISubReference subReference = subreferences.get(subReferenceIndex);
		if (!(subReference instanceof ArraySubReference)) {
			ErrorReporter.INTERNAL_ERROR("Code generator reached erroneous type reference `" + getFullName() + "''");
			expression.expression.append("FATAL_ERROR encountered while processing `" + getFullName() + "''\n");
			return;
		}

		final IType nextType = ofType;
		final Value indexValue = ((ArraySubReference) subReference).getValue();
		final IReferenceChain referenceChain = ReferenceChain.getInstance(IReferenceChain.CIRCULARREFERENCE, true);
		final IValue last = indexValue.getValueRefdLast(CompilationTimeStamp.getBaseTimestamp(), referenceChain);
		referenceChain.release();

		expression.expression.append(MessageFormat.format("if({0}) '{'\n", globalId));
		closingBrackets.insert(0, "}\n");

		final String temporalIndexId = aData.getTemporaryVariableName();
		expression.expression.append(MessageFormat.format("final TitanInteger {0} = ", temporalIndexId));
		last.generateCodeExpressionMandatory(aData, expression, true);
		expression.expression.append(";\n");
		expression.expression.append(MessageFormat.format("{0} = {1}.is_greater_than_or_equal(0) && {1}.is_less_than({2}.{3});\n",
				globalId, temporalIndexId, externalId, isTemplate?"n_elem()":"size_of()"));

		expression.expression.append(MessageFormat.format("if({0}) '{'\n", globalId));
		closingBrackets.insert(0, "}\n");

		final String temporalId = aData.getTemporaryVariableName();
		if (isTemplate) {
			expression.expression.append(MessageFormat.format("final {0} {1} = {2}.constGet_at({3});\n", nextType.getGenNameTemplate(aData, expression.expression, myScope),
					temporalId, externalId, temporalIndexId));
		} else {
			expression.expression.append(MessageFormat.format("final {0} {1} = {2}.constGet_at({3});\n", nextType.getGenNameValue(aData, expression.expression, myScope),
					temporalId, externalId, temporalIndexId));
		}

		final boolean isLast = subReferenceIndex == (subreferences.size() - 1);
		if (optype == Operation_type.ISBOUND_OPERATION) {
			expression.expression.append(MessageFormat.format("{0} = {1}.is_bound();\n", globalId, temporalId));
		} else if (optype == Operation_type.ISPRESENT_OPERATION) {
			expression.expression.append(MessageFormat.format("{0} = {1}.{2}({3});\n", globalId,  temporalId, !isLast?"is_bound":"is_present", isLast && isTemplate && aData.getAllowOmitInValueList()?"true":""));
		} else if (optype == Operation_type.ISCHOOSEN_OPERATION) {
			expression.expression.append(MessageFormat.format("{0} = {1}.is_bound();\n", globalId, temporalId));
			if (subReferenceIndex==subreferences.size()-1) {
				expression.expression.append(MessageFormat.format("if ({0}) '{'\n", globalId));
				expression.expression.append(MessageFormat.format("{0} = {1}.ischosen({2});\n", globalId, temporalId, field));
				expression.expression.append("}\n");
			}
		}

		nextType.generateCodeIsPresentBoundChosen(aData, expression, subreferences, subReferenceIndex + 1, globalId, temporalId, isTemplate, optype, field);

		expression.expression.append(closingBrackets);
	}

	@Override
	/** {@inheritDoc} */
	public boolean isPresentAnyvalueEmbeddedField(final ExpressionStruct expression, final List<ISubReference> subreferences, final int beginIndex) {
		return false;
	}
}
