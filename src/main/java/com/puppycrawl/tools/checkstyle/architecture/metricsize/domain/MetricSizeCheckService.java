package com.puppycrawl.tools.checkstyle.architecture.metricsize.domain;

import com.puppycrawl.tools.checkstyle.architecture.metricsize.port.CheckExecutionPort;

public class MetricSizeCheckService {

    private final CheckExecutionPort checkExecutionPort;

    public MetricSizeCheckService(CheckExecutionPort checkExecutionPort) {
        this.checkExecutionPort = checkExecutionPort;
    }

    public void executeMetricOrSizeCheck() {
        validateExecutionPort();
        checkExecutionPort.executeCheck();
    }

    private void validateExecutionPort() {
        if (checkExecutionPort == null) {
            throw new IllegalStateException(
                    "Metric/Size check execution port must not be null.");
        }
    }
}