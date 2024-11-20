package app.helper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import app.Directories;
import app.Environment;
import app.input.IOUtils;
import app.input.InvalidInputException;
import game.ROM.LibScope;
import game.shared.ProjectDatabase;
import game.shared.lib.LibEntry;
import game.shared.lib.Library;

public class SymGenerator
{
	public static void main(String[] args) throws IOException, InvalidInputException
	{
		Environment.initialize();
		new SymGenerator();
		Environment.exit();
	}

	private SymGenerator() throws IOException
	{
		for (LibScope scope : LibScope.values()) {
			Library lib = ProjectDatabase.rom.getLibrary(scope);

			File out = new File(Directories.DATABASE + "PM64_" + scope + ".sym");
			PrintWriter pw = IOUtils.getBufferedPrintWriter(out);
			for (LibEntry e : lib) {
				switch (e.type) {
					case api:
						pw.printf("%08X,code,%s%n", e.address, "api_" + e.name);
						break;
					case asm:
						pw.printf("%08X,code,%s%n", e.address, e.name);
						break;
					case script:
						pw.printf("%08X,data,%s%n", e.address, "Script_" + e.name);
						break;
					case data:
						pw.printf("%08X,data,%s%n", e.address, e.name);
						break;
				}
			}
			pw.close();
		}

		File out = new File(Directories.DATABASE + "Paper Mario.nbm");
		PrintWriter pw = IOUtils.getBufferedPrintWriter(out);
		for (LibScope scope : new LibScope[] { LibScope.Common }) {
			Library lib = ProjectDatabase.rom.getLibrary(scope);
			pw.println(scope.name());
			for (LibEntry e : lib) {
				switch (e.type) {
					/*
					case api:
						pw.printf("\tCPU 0x%08X: %s%n", e.address, "api_" + e.name);
						break;
						*/
					case asm:
						pw.printf("\tCPU 0x%08X: %s%n", e.address, e.name);
						break;
					/*
					case script:
						pw.printf("\tMEM 0x%08X: %s%n", e.address, "Script_" + e.name);
						break;
					case data:
						pw.printf("\tMEM 0x%08X: %s%n", e.address, e.name);
						break;
					*/
				}
			}
		}
		pw.close();
	}
}
