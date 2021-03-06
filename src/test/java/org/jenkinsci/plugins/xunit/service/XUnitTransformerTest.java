package org.jenkinsci.plugins.xunit.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.lib.dtkit.model.InputMetricType;
import org.jenkinsci.lib.dtkit.model.InputMetricXSL;
import org.jenkinsci.lib.dtkit.model.InputType;
import org.jenkinsci.lib.dtkit.model.OutputMetric;
import org.jenkinsci.plugins.xunit.types.model.JUnitModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Stage;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

@RunWith(MockitoJUnitRunner.class)
public class XUnitTransformerTest {

    @Mock
    private TaskListener buildListenerMock;
    @Mock
    private XUnitLog xUnitLogMock;
    @Mock
    private XUnitReportProcessorService xUnitReportProcessorServiceMock;
    @Mock
    private XUnitConversionService xUnitConversionServiceMock;
    @Mock
    private XUnitValidationService xUnitValidationServiceMock;
    @Mock
    private XUnitToolInfo xUnitToolInfoMock;
    private XUnitTransformerCallable xUnitTransformer;

    @Rule
    public TemporaryFolder folderRule = new TemporaryFolder();


    @Before
    public void beforeTest() throws IOException {
        when(xUnitToolInfoMock.getInputMetric()).thenReturn(new MyInputMetric());

        xUnitTransformer = Guice.createInjector(Stage.DEVELOPMENT, new AbstractModule() {
            @Override
            protected void configure() {
                bind(TaskListener.class).toInstance(buildListenerMock);
                bind(XUnitLog.class).toInstance(xUnitLogMock);
                bind(XUnitToolInfo.class).toInstance(xUnitToolInfoMock);
                bind(XUnitConversionService.class).toInstance(xUnitConversionServiceMock);
                bind(XUnitValidationService.class).toInstance(xUnitValidationServiceMock);
                bind(XUnitReportProcessorService.class).toInstance(xUnitReportProcessorServiceMock);
            }
        }).getInstance(XUnitTransformerCallable.class);
        xUnitTransformer.setProcessorId("testProcessor");
    }

    @SuppressWarnings("serial")
    public static class MyInputMetric extends InputMetricXSL {
        @Override
        public String getToolName() {
            return "testTool";
        }

        @Override
        public String getToolVersion() {
            return "testVersion";
        }

        @Override
        public InputMetricType getInputMetricType() {
            return InputMetricType.XSL;
        }

        @Override
        public InputType getToolType() {
            return InputType.TEST;
        }

        @Override
        public String getXslName() {
            return null;
        }

        @Override
        public String[] getInputXsdNameList() {
            return null;
        }

        @Override
        public OutputMetric getOutputFormatType() {
            return JUnitModel.LATEST;
        }
    }

    @Test(expected = NoNewTestReportException.class)
    public void verify_that_fails_when_there_are_no_new_tests_report() throws Exception {

        // Recording behaviour : testing not empty result files found and a
        // false check
        String fileName = "a.txt";
        List<String> resultFiles = Arrays.asList(fileName);
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        File ws = folderRule.newFolder();
        doThrow(NoNewTestReportException.class).when(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(any(XUnitToolInfo.class), Mockito.<String>anyList(), any(File.class));

        // Test result
        try {
            xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        } catch (NoNewTestReportException e) {
            // Verifying
            verify(xUnitValidationServiceMock, never()).validateInputFile(any(XUnitToolInfo.class), any(File.class));
            verify(xUnitConversionServiceMock, never()).convert(any(XUnitToolInfo.class), any(File.class), any(File.class));
            verify(xUnitValidationServiceMock, never()).validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class));

            InOrder inOrder = inOrder(xUnitReportProcessorServiceMock);
            inOrder.verify(xUnitReportProcessorServiceMock).findReports(any(XUnitToolInfo.class), eq(ws), (String) isNull());
            inOrder.verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(any(XUnitToolInfo.class), eq(resultFiles), eq(ws));
            
            throw e;
        }
    }

    @Test(expected = EmptyReportFileException.class)
    public void empty_test_report_throws_exception_when_stop_build_option_is_true() throws Exception {
        // One result file
        List<String> resultFiles = Arrays.asList("a.txt");
        when(xUnitReportProcessorServiceMock.findReports(eq(xUnitToolInfoMock), any(File.class), (String) any())).thenReturn(resultFiles);

        // Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(true);

        // Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenCallRealMethod();

        // Create a empty file
        File ws = folderRule.newFolder();
        File myInputFile = new File(ws, "a.txt");
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), (String) any())).thenReturn(myInputFile);

        try {
            xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        } catch (EmptyReportFileException e) {
            // Verifying mock interactions
            InOrder inOrder = inOrder(xUnitReportProcessorServiceMock);
            inOrder.verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
            inOrder.verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(any(XUnitToolInfo.class), eq(resultFiles), eq(ws));
            inOrder.verify(xUnitReportProcessorServiceMock).getCurrentReport(ws, resultFiles.get(0));

            // Theses methods are never call
            verify(xUnitValidationServiceMock, never()).validateInputFile(any(XUnitToolInfo.class), any(File.class));
            verify(xUnitConversionServiceMock, never()).convert(any(XUnitToolInfo.class), any(File.class), any(File.class));
            verify(xUnitValidationServiceMock, never()).validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class));

            throw e;
        }
    }

    @Test
    public void empty_test_report_does_not_throws_exception_when_stop_build_option_is_false() throws Exception {
        //One result file
        List<String> resultFiles = Arrays.asList("a.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(false);

        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenCallRealMethod();

        //Create an empty file
        //Test the process continues and prints a message
        File ws = folderRule.newFolder();
        File myInputFile = new File(ws, "a.txt");
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), anyString())).thenReturn(myInputFile);

        //The process exits on true
        xUnitTransformer.invoke(ws, mock(VirtualChannel.class));

        //Verifying mock interactions
        InOrder inOrder = inOrder(xUnitReportProcessorServiceMock);
        inOrder.verify(xUnitReportProcessorServiceMock).findReports(any(XUnitToolInfo.class), eq(ws), (String) isNull());
        inOrder.verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(any(XUnitToolInfo.class), eq(resultFiles), eq(ws));
        inOrder.verify(xUnitReportProcessorServiceMock).getCurrentReport(ws, resultFiles.get(0));

        verify(xUnitLogMock).warn(startsWith("The result file '" + myInputFile.getAbsolutePath() + "' for the metric 'testTool' is empty. The result file has been skipped."));

        // Theses methods are never call
        verify(xUnitValidationServiceMock, never()).validateInputFile(any(XUnitToolInfo.class), any(File.class));
        verify(xUnitConversionServiceMock, never()).convert(any(XUnitToolInfo.class), any(File.class), any(File.class));
        verify(xUnitValidationServiceMock, never()).validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class));
    }

    @Test
    public void verify_normal_validation_process() throws Exception {
        // create a not empty report file
        File ws = folderRule.newFolder();
        File myInputFile = new File(ws, "a.txt");
        FileUtils.write(myInputFile, "bidon");

        List<String> resultFiles = Arrays.asList("a.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), anyString())).thenReturn(myInputFile);
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenCallRealMethod();
        // Mark halt processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(true);

        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(true);
        when(xUnitConversionServiceMock.convert(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(new File(ws, "output"));
        when(xUnitValidationServiceMock.validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(true);

        //The process exits on true
        xUnitTransformer.invoke(ws, mock(VirtualChannel.class));

        //Verifying mock interactions
        InOrder inOrderReport = inOrder(xUnitReportProcessorServiceMock);
        inOrderReport.verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), any(File.class), (String) isNull());
        inOrderReport.verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
        inOrderReport.verify(xUnitReportProcessorServiceMock).getCurrentReport(eq(ws), anyString());

        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile));
        verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFile), any(File.class));
        verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile), any(File.class));
    }

    @Test(expected = EmptyReportFileException.class)
    public void an_empty_test_report_stop_process_when_stop_build_option_is_true() throws Exception {
        //Create test reports, a.txt not empty and b.txt empty
        File ws = folderRule.newFolder();
        File myInputFileNotEmpty = new File(ws, "a.txt");
        FileUtils.write(myInputFileNotEmpty, "bidon");
        File myInputFileEmpty = new File(ws, "b.txt");

        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(true);

        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenCallRealMethod();

        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFileNotEmpty);
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("b.txt"))).thenReturn(myInputFileEmpty);

        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(true);
        when(xUnitConversionServiceMock.convert(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(new File(ws, "output"));
        when(xUnitValidationServiceMock.validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(true);

        try {
            xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        } catch(EmptyReportFileException e) {
            //Verifying mock interactions
            verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
            verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
            verify(xUnitReportProcessorServiceMock, times(2)).getCurrentReport(eq(ws), anyString());
            
            verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty));
            verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty), any(File.class));
            verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty), any(File.class));

            verify(xUnitValidationServiceMock, never()).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFileEmpty));
            verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFileEmpty), any(File.class));
            verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFileEmpty), any(File.class));
            
            throw e;
        }
    }

    @Test
    public void an_empty_test_report_do_not_stop_process_when_stop_build_option_is_false() throws Exception {
        //Create test reports, a.txt not empty and b.txt empty
        File ws = folderRule.newFolder();
        File myInputFileNotEmpty = new File(ws, "a.txt");
        FileUtils.write(myInputFileNotEmpty, "bidon");
        File myInputFileEmpty = new File(ws, "b.txt");

        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(false);

        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenCallRealMethod();

        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFileNotEmpty);
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("b.txt"))).thenReturn(myInputFileEmpty);

        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(true);
        when(xUnitConversionServiceMock.convert(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(new File(ws, "output"));
        when(xUnitValidationServiceMock.validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(true);

        xUnitTransformer.invoke(ws, mock(VirtualChannel.class));

        //Verifying mock interactions
        verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
        verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
        verify(xUnitReportProcessorServiceMock, times(2)).getCurrentReport(eq(ws), anyString());
        
        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty));
        verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty), any(File.class));
        verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFileNotEmpty), any(File.class));
        
        verify(xUnitValidationServiceMock, never()).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFileEmpty));
        verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFileEmpty), any(File.class));
        verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFileEmpty), any(File.class));
    }

    @Test(expected = TransformerException.class)
    public void invalid_input_report_throws_exception_when_stop_build_option_is_true() throws Exception {
        File ws = folderRule.newFolder();
        File myInputFile1 = new File(ws, "a.txt");
        FileUtils.write(myInputFile1, "bidon");
        File myInputFile2 = new File(ws, "b.txt");
        FileUtils.write(myInputFile2, "bidon");

        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(true);

        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenReturn(true);

        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFile1);

        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(false);

        try {
            xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        } catch (TransformerException e) {
            //Verifying mock interactions
            verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
            verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
            verify(xUnitReportProcessorServiceMock).getCurrentReport(eq(ws), anyString());
            
            verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile1));
            verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
            verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));

            verify(xUnitValidationServiceMock, never()).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile2));
            verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
            verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
            
            throw e;
        }
    }

    @Test
    public void invalid_input_report_does_not_throws_exception_when_stop_build_option_is_false() throws Exception {
        File ws = folderRule.newFolder();
        File myInputFile1 = new File(ws, "a.txt");
        FileUtils.write(myInputFile1, "bidon");
        File myInputFile2 = new File(ws, "b.txt");
        FileUtils.write(myInputFile2, "bidon");

        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);

        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(false);

        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenReturn(true);

        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFile1);
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("b.txt"))).thenReturn(myInputFile2);

        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(false);

        xUnitTransformer.invoke(ws, mock(VirtualChannel.class));

        //Verifying mock interactions
        verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
        verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
        
        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile1));
        verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
        verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
        
        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile2));
        verify(xUnitConversionServiceMock, never()).convert(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
        verify(xUnitValidationServiceMock, never()).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
    }

    @Test(expected = TransformerException.class)
    public void invalid_output_report_throws_exception_when_stop_build_option_is_true() throws Exception {
        File ws = folderRule.newFolder();
        File myInputFile1 = new File(ws, "a.txt");
        FileUtils.write(myInputFile1, "bidon");
        File myInputFile2 = new File(ws, "b.txt");
        FileUtils.write(myInputFile2, "bidon");
        
        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);
        
        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(true);
        
        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenReturn(true);
        
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFile1);
        
        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(true);
        when(xUnitConversionServiceMock.convert(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(new File(ws, "output"));
        when(xUnitValidationServiceMock.validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(false);
        
        try {
            xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        } catch (TransformerException e) {
            //Verifying mock interactions
            verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
            verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
            verify(xUnitReportProcessorServiceMock).getCurrentReport(eq(ws), anyString());
            
            verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile1));
            verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
            verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
            
            verify(xUnitValidationServiceMock, never()).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile2));
            
            throw e;
        }
    }
    
    @Test
    public void invalid_output_report_does_not_throws_exception_when_stop_build_option_is_false() throws Exception {
        File ws = folderRule.newFolder();
        File myInputFile1 = new File(ws, "a.txt");
        FileUtils.write(myInputFile1, "bidon");
        File myInputFile2 = new File(ws, "b.txt");
        FileUtils.write(myInputFile2, "bidon");
        
        List<String> resultFiles = Arrays.asList("a.txt", "b.txt");
        when(xUnitReportProcessorServiceMock.findReports(any(XUnitToolInfo.class), any(File.class), (String) any())).thenReturn(resultFiles);
        
        //Stop processing when there is an error
        when(xUnitReportProcessorServiceMock.isStopProcessingIfError(any(XUnitToolInfo.class))).thenReturn(false);
        
        //Wants to call the real method checkFileIsNotEmpty
        when(xUnitValidationServiceMock.checkFileIsNotEmpty(any(File.class))).thenReturn(true);
        
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("a.txt"))).thenReturn(myInputFile1);
        when(xUnitReportProcessorServiceMock.getCurrentReport(any(File.class), eq("b.txt"))).thenReturn(myInputFile2);
        
        //Case: Right input validation, conversion and right output validation
        when(xUnitValidationServiceMock.validateInputFile(any(XUnitToolInfo.class), any(File.class))).thenReturn(true);
        when(xUnitConversionServiceMock.convert(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(new File(ws, "output"));
        when(xUnitValidationServiceMock.validateOutputFile(any(XUnitToolInfo.class), any(File.class), any(File.class))).thenReturn(false);
        
        xUnitTransformer.invoke(ws, mock(VirtualChannel.class));
        
        //Verifying mock interactions
        verify(xUnitReportProcessorServiceMock).findReports(eq(xUnitToolInfoMock), eq(ws), (String) isNull());
        verify(xUnitReportProcessorServiceMock).checkIfFindsFilesNewFiles(eq(xUnitToolInfoMock), eq(resultFiles), eq(ws));
        
        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile1));
        verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
        verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile1), any(File.class));
        
        verify(xUnitValidationServiceMock).validateInputFile(eq(xUnitToolInfoMock), eq(myInputFile2));
        verify(xUnitConversionServiceMock).convert(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
        verify(xUnitValidationServiceMock).validateOutputFile(eq(xUnitToolInfoMock), eq(myInputFile2), any(File.class));
    }

}