package bms.player.beatoraja.stream;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NamedPipeSender {
	private static final Logger logger = LoggerFactory.getLogger(NamedPipeSender.class);
	private static final int MAX_SEND_ATTEMPTS = 8;
	private static final long INITIAL_RETRY_DELAY_MS = 50L;
	private static final long MAX_RETRY_DELAY_MS = 1000L;

	private final String pipePath;

	public NamedPipeSender(String pipeName) {
		pipePath = "\\\\.\\pipe\\" + pipeName;
	}

	public void sendLine(String line) {
		long retryDelay = INITIAL_RETRY_DELAY_MS;
		Exception lastError = null;
		int attempts = 0;
		boolean interrupted = false;
		for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
			attempts = attempt;
			try {
				writeLine(line);
				dump(line, null, attempt);
				if (attempt > 1) {
					logger.info("Named pipe送信成功({}) attempt={}", pipePath, attempt);
				}
				return;
			} catch (Exception e) {
				lastError = e;
				dump(line, e, attempt);
				if (attempt < MAX_SEND_ATTEMPTS) {
					sleepBeforeRetry(retryDelay);
					if (Thread.currentThread().isInterrupted()) {
						interrupted = true;
						break;
					}
					retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
				}
			}
		}
		if (interrupted) {
			logger.debug("Named pipe送信中断({}) attempts={} error={}", pipePath, attempts,
					lastError != null ? lastError.getMessage() : "");
		} else {
			logger.debug("Named pipe送信失敗({}) attempts={} error={}", pipePath, attempts,
					lastError != null ? lastError.getMessage() : "");
		}
	}

	private void writeLine(String line) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(pipePath), StandardCharsets.UTF_8))) {
			writer.write(line);
			writer.newLine();
			writer.flush();
		}
	}

	private void sleepBeforeRetry(long retryDelay) {
		try {
			Thread.sleep(retryDelay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void dump(String line, Exception error, int attempt) {
		try {
			Path logDir = Path.of("log");
			Files.createDirectories(logDir);
			StringBuilder dump = new StringBuilder();
			dump.append('{');
			appendJsonField(dump, "time", LocalDateTime.now().toString()).append(',');
			appendJsonField(dump, "pipe", pipePath).append(',');
			appendJsonField(dump, "status", error == null ? "sent" : "failed").append(',');
			dump.append("\"attempt\":").append(attempt).append(',');
			appendJsonField(dump, "error", error == null ? "" : error.getMessage()).append(',');
			appendJsonField(dump, "line", line);
			dump.append("}\n");
			Files.writeString(logDir.resolve("oraja_helper_pipe_send.jsonl"), dump.toString(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
	}

	private StringBuilder appendJsonField(StringBuilder builder, String name, String value) {
		builder.append('"').append(escapeJson(name)).append("\":\"").append(escapeJson(value)).append('"');
		return builder;
	}

	private String escapeJson(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder escaped = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
			case '"' -> escaped.append("\\\"");
			case '\\' -> escaped.append("\\\\");
			case '\b' -> escaped.append("\\b");
			case '\f' -> escaped.append("\\f");
			case '\n' -> escaped.append("\\n");
			case '\r' -> escaped.append("\\r");
			case '\t' -> escaped.append("\\t");
			default -> {
				if (c < 0x20) {
					escaped.append(String.format("\\u%04x", (int) c));
				} else {
					escaped.append(c);
				}
			}
			}
		}
		return escaped.toString();
	}
}
