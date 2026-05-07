package com.puppycrawl.tools.checkstyle.architecture.metricsize.domain;

import com.puppycrawl.tools.checkstyle.architecture.metricsize.port.in.RunMetricSizeCheckUseCase;
import com.puppycrawl.tools.checkstyle.architecture.metricsize.port.out.CheckExecutionPort;

public class MetricSizeCheckService implements RunMetricSizeCheckUseCase {

    private final CheckExecutionPort checkExecutionPort;

    public MetricSizeCheckService(CheckExecutionPort checkExecutionPort) {
        this.checkExecutionPort = checkExecutionPort;
    }

    @Override
    public void run() {
        checkExecutionPort.execute();
    }
}