package com.puppycrawl.tools.checkstyle.architecture.metricsize.adapter;

import com.puppycrawl.tools.checkstyle.architecture.metricsize.port.CheckExecutionPort;

public class CheckstyleCheckExecutionAdapter implements CheckExecutionPort {

    private boolean executed;

    @Override
    public void executeCheck() {
        executed = true;
    }

    public boolean isExecuted() {
        return executed;
    }
}