package com.osgateway.reporting.application;

public record ExportFile(byte[] content, String filename, String contentType) {
}
