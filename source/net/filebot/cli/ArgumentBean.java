package net.filebot.cli;

import static java.util.Collections.*;
import static net.filebot.Logging.*;
import static net.filebot.hash.VerificationUtilities.*;
import static net.filebot.subtitle.SubtitleUtilities.*;
import static net.filebot.util.FileUtilities.*;

import java.io.File;
import java.io.FileFilter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

import net.filebot.Language;
import net.filebot.StandardRenameAction;
import net.filebot.WebServices;
import net.filebot.format.ExpressionFileFilter;
import net.filebot.format.ExpressionFilter;
import net.filebot.format.ExpressionFormat;
import net.filebot.hash.HashType;
import net.filebot.subtitle.SubtitleFormat;
import net.filebot.subtitle.SubtitleNaming;
import net.filebot.web.Datasource;
import net.filebot.web.SortOrder;

public class ArgumentBean {

	@Option(name = "--mode", usage = "Open GUI in single panel mode / Enable CLI interactive mode", metaVar = "[Rename, Subtitles, SFV] or [interactive]")
	public String mode = null;

	@Option(name = "-rename", usage = "Rename media files")
	public boolean rename = false;

	@Option(name = "--db", usage = "Database", metaVar = "[TheTVDB, AniDB] or [TheMovieDB] or [AcoustID, ID3] or [xattr]")
	public String db;

	@Option(name = "--order", usage = "Episode order", metaVar = "[Airdate, Absolute, DVD]")
	public String order = "Airdate";

	@Option(name = "--action", usage = "Rename action", metaVar = "[move, copy, keeplink, symlink, hardlink, reflink, test]")
	public String action = "move";

	@Option(name = "--conflict", usage = "Conflict resolution", metaVar = "[skip, override, auto, index, fail]")
	public String conflict = "skip";

	@Option(name = "--filter", usage = "Filter expression", metaVar = "expression")
	public String filter = null;

	@Option(name = "--format", usage = "Format expression", metaVar = "expression")
	public String format;

	@Option(name = "-non-strict", usage = "Enable advanced matching and more aggressive guessing")
	public boolean nonStrict = false;

	@Option(name = "-get-subtitles", usage = "Fetch subtitles")
	public boolean getSubtitles;

	@Option(name = "--q", usage = "Force lookup query", metaVar = "series/movie title")
	public String query;

	@Option(name = "--lang", usage = "Language", metaVar = "3-letter language code")
	public String lang = "en";

	@Option(name = "-check", usage = "Create/Check verification files")
	public boolean check;

	@Option(name = "--output", usage = "Output path", metaVar = "/path")
	public String output;

	@Option(name = "--encoding", usage = "Output character encoding", metaVar = "[UTF-8, Windows-1252]")
	public String encoding;

	@Option(name = "-list", usage = "Fetch episode list")
	public boolean list = false;

	@Option(name = "-mediainfo", usage = "Get media info")
	public boolean mediaInfo = false;

	@Option(name = "-revert", usage = "Revert files")
	public boolean revert = false;

	@Option(name = "-extract", usage = "Extract archives")
	public boolean extract = false;

	@Option(name = "-script", usage = "Run Groovy script", metaVar = "[fn:name] or [dev:name] or [/path/to/script.groovy]")
	public String script = null;

	@Option(name = "--log", usage = "Log level", metaVar = "[all, fine, info, warning]")
	public String log = "all";

	@Option(name = "--log-file", usage = "Log file", metaVar = "/path/to/log.txt")
	public String logFile = null;

	@Option(name = "--log-lock", usage = "Lock log file", metaVar = "[yes, no]", handler = ExplicitBooleanOptionHandler.class)
	public boolean logLock = true;

	@Option(name = "-r", usage = "Recursively process folders")
	public boolean recursive = false;

	@Option(name = "-clear-cache", usage = "Clear cached and temporary data")
	public boolean clearCache = false;

	@Option(name = "-clear-prefs", usage = "Clear application settings")
	public boolean clearPrefs = false;

	@Option(name = "-unixfs", usage = "Do not strip invalid characters from file paths")
	public boolean unixfs = false;

	@Option(name = "-no-xattr", usage = "Disable extended attributes")
	public boolean disableExtendedAttributes = false;

	@Option(name = "-version", usage = "Print version identifier")
	public boolean version = false;

	@Option(name = "-help", usage = "Print this help message")
	public boolean help = false;

	@Option(name = "--def", usage = "Define script variables", handler = BindingsHandler.class)
	public Map<String, String> defines = new LinkedHashMap<String, String>();

	@Argument
	public List<String> arguments = new ArrayList<String>();

	public boolean runCLI() {
		return rename || getSubtitles || check || list || mediaInfo || revert || extract || script != null;
	}

	public boolean isInteractive() {
		return "interactive".equalsIgnoreCase(mode) && System.console() != null;
	}

	public boolean printVersion() {
		return version;
	}

	public boolean printHelp() {
		return help;
	}

	public boolean clearCache() {
		return clearCache;
	}

	public boolean clearUserData() {
		return clearPrefs;
	}

	public List<File> getFiles(boolean resolveFolders) {
		if (arguments == null || arguments.isEmpty()) {
			return emptyList();
		}

		// resolve given paths
		List<File> files = new ArrayList<File>();

		for (String it : arguments) {
			// ignore empty arguments
			if (it.trim().isEmpty())
				continue;

			// resolve relative paths
			File file = new File(it);

			try {
				file = file.getCanonicalFile();
			} catch (Exception e) {
				debug.warning(format("Illegal Argument: %s (%s)", e, file));
			}

			if (resolveFolders && file.isDirectory()) {
				if (recursive) {
					files.addAll(listFiles(file, FILES, HUMAN_NAME_ORDER));
				} else {
					files.addAll(getChildren(file, f -> f.isFile() && !f.isHidden(), HUMAN_NAME_ORDER));
				}
			} else {
				files.add(file);
			}
		}

		return files;
	}

	public StandardRenameAction getRenameAction() {
		return StandardRenameAction.forName(action);
	}

	public ConflictAction getConflictAction() {
		return ConflictAction.forName(conflict);
	}

	public SortOrder getSortOrder() {
		return SortOrder.forName(order);
	}

	public ExpressionFormat getExpressionFormat() throws Exception {
		return format == null ? null : new ExpressionFormat(format);
	}

	public ExpressionFilter getExpressionFilter() throws Exception {
		return filter == null ? null : new ExpressionFilter(filter);
	}

	public FileFilter getExpressionFileFilter() throws Exception {
		return filter == null ? null : new ExpressionFileFilter(filter);
	}

	public Datasource getDatasource() {
		return db == null ? null : WebServices.getService(db);
	}

	public String getSearchQuery() {
		return query == null || query.isEmpty() ? null : query;
	}

	public File getOutputPath() {
		return output == null ? null : new File(output);
	}

	public File getAbsoluteOutputFolder() throws Exception {
		return output == null ? null : new File(output).getCanonicalFile();
	}

	public SubtitleFormat getSubtitleOutputFormat() {
		return output == null ? null : getSubtitleFormatByName(output);
	}

	public SubtitleNaming getSubtitleNamingFormat() {
		return optional(format).map(SubtitleNaming::forName).orElse(SubtitleNaming.MATCH_VIDEO_ADD_LANGUAGE_TAG);
	}

	public HashType getOutputHashType() {
		// support --output checksum.sfv
		return optional(output).map(File::new).map(f -> getHashType(f)).orElseGet(() -> {
			// support --format SFV
			return optional(format).map(k -> getHashTypeByExtension(k)).orElse(HashType.SFV);
		});
	}

	public Charset getEncoding() {
		return encoding == null ? null : Charset.forName(encoding);
	}

	public Language getLanguage() {
		// find language code for any input (en, eng, English, etc)
		return optional(lang).map(Language::findLanguage).orElseThrow(error("Illegal language code", lang));
	}

	public boolean isStrict() {
		return !nonStrict;
	}

	public Level getLogLevel() {
		return Level.parse(log.toUpperCase());
	}

	private final String[] args;

	public ArgumentBean(String... args) throws CmdLineException {
		this.args = args;

		CmdLineParser parser = new CmdLineParser(this);
		parser.parseArgument(args);
	}

	public String[] getArgumentArray() {
		return args.clone();
	}

	public String usage() {
		StringWriter buffer = new StringWriter();
		CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withShowDefaults(false).withOptionSorter(null));
		parser.printUsage(buffer, null);
		return buffer.toString();
	}

	private static <T> Optional<T> optional(T value) {
		return Optional.ofNullable(value);
	}

	private static Supplier<CmdlineException> error(String message, Object value) {
		return () -> new CmdlineException(message + ": " + value);
	}

}
