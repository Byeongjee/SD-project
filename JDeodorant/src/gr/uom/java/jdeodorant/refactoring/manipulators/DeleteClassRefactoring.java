package gr.uom.java.jdeodorant.refactoring.manipulators;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreateCompilationUnitChange;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringChangeDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObjectCandidate;
import gr.uom.java.ast.SystemObject;

/**
 * Resolve Unnecessary Generalization in Class Hierarchy,
 * and Progress Refactoring 
 * @author JuYong Lee
 */
public class DeleteClassRefactoring extends Refactoring {
	private ClassObjectCandidate targetClass;
	
	private List<String> originalContentList;
	private String refactoredContent;

	private CompilationUnitChange compilationUnitChange;
			
	public DeleteClassRefactoring(ClassObjectCandidate target) {
		targetClass = target;
		this.originalContentList = this.targetClass.getContent();
		refactoredContent = "";

		IFile _file = targetClass.getIFile();
		ICompilationUnit sourceICompilationUnit = (ICompilationUnit) JavaCore.create(_file);
		this.compilationUnitChange = new CompilationUnitChange("", sourceICompilationUnit);
	}
	
	public DeleteClassRefactoring(CompilationUnit sourceCompilationUnit) {
		this.originalContentList = this.targetClass.getContent();
		refactoredContent = "";

		ICompilationUnit sourceICompilationUnit = (ICompilationUnit) sourceCompilationUnit.getJavaElement();
		this.compilationUnitChange = new CompilationUnitChange("", sourceICompilationUnit);
	}

	/**
	 *  Rather than Deleting, Make the codes to be comments for stability issue
	 */
	public void commentizeWholeContent() {
		// Wrap as comments
		List<String> resolvedClassContent = this.originalContentList;
		String newContent = "/*\r\n";
		for( String c : resolvedClassContent) {
			newContent += c + "\r\n";
		}
		newContent += "\r\n*/";
		this.refactoredContent = newContent;

		/*		
 		IFile _file = targetClass.getIFile();
		ICompilationUnit sourceICompilationUnit = (ICompilationUnit) JavaCore.create(_file);
		CompilationUnit _cu = null;
		ASTRewrite sourceRewriter = null;
		try {
			_cu = sourceICompilationUnit.reconcile(ICompilationUnit.ENABLE_STATEMENTS_RECOVERY, true, null, null);
			sourceRewriter = ASTRewrite.create( _cu.getAST() );
		} catch (JavaModelException e1) {
			e1.printStackTrace();
		}
		IDocument document = new Document("");
		sourceRewriter.rewriteAST(document, null);
		System.out.println( document.get() );
		*/
		
		// Preview
		try {
			MultiTextEdit root = new MultiTextEdit();
			compilationUnitChange.dispose();
			compilationUnitChange.setEdit(root);
			root.addChild(new InsertEdit(0, this.refactoredContent));
			compilationUnitChange.addTextEditGroup(new TextEditGroup("Refactored Source", root.getChildren()));
		} catch (MalformedTreeException e) {
			e.printStackTrace();
		}
		
		return;
	}
	
	/**
	 *  Rewrite The Contents
	 */
	public void processRefactoring() {		
		SystemObject systemObject = ASTReader.getSystemObject();
		if (systemObject != null) {
			IFile _file = targetClass.getIFile();
			ICompilationUnit _compilationUnit = (ICompilationUnit) JavaCore.create(_file);

			try {
				ICompilationUnit _CUorigin = _compilationUnit.getWorkingCopy(new WorkingCopyOwner() {}, null);
				IBuffer _bufferOrigin = ((IOpenable) _CUorigin).getBuffer();
				_bufferOrigin.replace(0, _bufferOrigin.getLength(), this.refactoredContent);
				_CUorigin.reconcile(ICompilationUnit.NO_AST, false, null, null);
				
				_CUorigin.commitWorkingCopy(false, null);
				_CUorigin.discardWorkingCopy();
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public String getName() {
		return "Delete Class";
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		final RefactoringStatus status= new RefactoringStatus();
		try {
			pm.beginTask("Checking preconditions...", 2);
			this.commentizeWholeContent();
		} finally {
			pm.done();
		}
		return status;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		RefactoringStatus status= new RefactoringStatus();
		try {
			pm.beginTask("Checking preconditions...", 1);
		} finally {
			pm.done();
		}
		return status;
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException,
			OperationCanceledException {
		try {
			pm.beginTask("Creating change...", 1);
			final List<TextFileChange> changes = new ArrayList<TextFileChange>();
			changes.add(compilationUnitChange);
			
			CompositeChange change = new CompositeChange(getName(), changes.toArray(new Change[changes.size()])) {
				@Override
				public ChangeDescriptor getDescriptor() {
					return null;
				}
			};
			
			return change;
		} finally {
			pm.done();
		}
	}
	
	
}
