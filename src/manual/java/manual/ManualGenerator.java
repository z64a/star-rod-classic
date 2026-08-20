package manual;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public final class ManualGenerator
{
	private static final List<Extension> EXTENSIONS = Arrays.asList(TablesExtension.create(), HeadingAnchorExtension.create());
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
	private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

	private ManualGenerator()
	{}

	public static void main(String[] args) throws IOException
	{
		if (args.length != 2)
			throw new IllegalArgumentException("Usage: ManualGenerator <source directory> <output directory>");

		Path sourceDirectory = Path.of(args[0]);
		Path outputDirectory = Path.of(args[1]);

		Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException
			{
				Files.createDirectories(outputDirectory.resolve(sourceDirectory.relativize(directory)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path sourceFile, BasicFileAttributes attrs) throws IOException
			{
				Path relativePath = sourceDirectory.relativize(sourceFile);
				Path outputFile = outputDirectory.resolve(relativePath);

				if (sourceFile.getFileName().toString().endsWith(".md")) {
					String filename = outputFile.getFileName().toString();
					outputFile = outputFile.resolveSibling(filename.substring(0, filename.length() - 3) + ".html");
					renderPage(sourceFile, outputFile, outputDirectory);
				}
				else {
					Files.copy(sourceFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
				}

				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void renderPage(Path sourceFile, Path outputFile, Path outputDirectory) throws IOException
	{
		String markdown = Files.readString(sourceFile, StandardCharsets.UTF_8);
		String body = RENDERER.render(PARSER.parse(markdown));
		body = body.replace(".md#", ".html#").replace(".md\"", ".html\"");

		String stylesheet = relativeUrl(outputFile, outputDirectory.resolve("manual.css"));
		String contents = relativeUrl(outputFile, outputDirectory.resolve("README.html"));
		String title = escapeHtml(findTitle(markdown));
		String navigation = outputFile.equals(outputDirectory.resolve("README.html")) ? "" : "<nav><a href=\"" + contents + "\">User Guide</a></nav>\n";
		String html = "<!doctype html>\n"
			+ "<html lang=\"en\">\n"
			+ "<head>\n"
			+ "<meta charset=\"utf-8\">\n"
			+ "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
			+ "<title>" + title + "</title>\n"
			+ "<link rel=\"stylesheet\" href=\"" + stylesheet + "\">\n"
			+ "</head>\n"
			+ "<body>\n"
			+ "<main>\n"
			+ navigation
			+ body
			+ "</main>\n"
			+ "</body>\n"
			+ "</html>\n";

		Files.writeString(outputFile, html, StandardCharsets.UTF_8);
	}

	private static String relativeUrl(Path sourceFile, Path targetFile)
	{
		return sourceFile.getParent().relativize(targetFile).toString().replace('\\', '/');
	}

	private static String findTitle(String markdown)
	{
		for (String line : markdown.split("\\R")) {
			if (line.startsWith("# "))
				return line.substring(2).replace("`", "");
		}

		return "Star Rod Classic User Guide";
	}

	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}
