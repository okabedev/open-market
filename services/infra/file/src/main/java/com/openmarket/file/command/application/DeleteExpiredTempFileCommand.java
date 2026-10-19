package com.openmarket.file.command.application;

import java.util.UUID;

/** 만료된 {@code TEMP} 파일 하나를 지운다. */
public record DeleteExpiredTempFileCommand(UUID fileId) {}
