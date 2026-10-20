package com.openmarket.file.command.application;

import java.util.UUID;

/** 만료된 {@code TEMP}·{@code DETACHED} 파일 하나를 지운다. */
public record DeleteExpiredFileCommand(UUID fileId) {}
