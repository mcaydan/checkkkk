package com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter.out;

import com.puppycrawl.tools.checkstyle.architecture.metricsize.port.out.CheckExecutionPort;

public class CheckstyleCheckExecutionAdapter implements CheckExecutionPort {

    @Override
    public void execute() {
        System.out.println("Executing metrics and size checks through adapter...");
    }
}