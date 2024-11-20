package game.map.scripts;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import app.Environment;
import app.input.IOUtils;
import app.input.InvalidInputException;
import common.Vector3f;
import game.map.Map;
import game.map.MapIndex;
import game.map.hit.CameraZoneData;
import game.map.hit.ControlType;
import game.map.marker.Marker;
import game.map.marker.Marker.MarkerType;
import util.Logger;

public class DecompScriptGenerator
{
	public final Map map;
	public final MapIndex index;

	private PrintWriter pw;

	public DecompScriptGenerator(Map map) throws IOException, InvalidInputException
	{
		this.map = map;
		this.index = new MapIndex(map);

		File cFile = new File(
			Environment.project.getDirectory(),
			"include/mapfs/" + map.name + ".gen.c"
		);

		Logger.log("Writing generated map scripts to " + cFile.getPath());

		pw = IOUtils.getBufferedPrintWriter(cFile);
		try {
			pw.println("#include \"mapfs/" + map.name + ".h\"");
			pw.println();
			addCameraTargets();
		}
		finally {
			pw.close();
		}
	}

	private void addCameraTargets() throws InvalidInputException
	{
		List<String> names = new ArrayList<>();

		for (Marker m : map.markerTree) {
			if (m.getType() == MarkerType.CamTarget) {
				String name = m.getName();
				if (names.contains(name))
					throw new InvalidInputException("Camera target name is not unique: " + name);
				names.add(name);

				String camScriptName = m.cameraComponent.generatePan.get() ? "cam_pan_" : "cam_set_";
				pw.println("Script N(" + camScriptName + m.getName() + ") = SCRIPT({");

				if (m.cameraComponent.useZone.get()) {
					Vector3f samplePos = m.position.getVector();
					pw.println(String.format("    UseSettingsFrom(0, %d, %d, %d);",
						Math.round(samplePos.x), Math.round(samplePos.y), Math.round(samplePos.z)));
					if (m.cameraComponent.overrideAngles.get())
						pw.println(String.format("    SetCamPitch(0, %f, %f);",
							m.cameraComponent.boomPitch.get(), m.cameraComponent.viewPitch.get()));
					if (m.cameraComponent.overrideDist.get())
						pw.println(String.format("    SetCamDistance(0, %d);",
							Math.round(m.cameraComponent.boomLength.get())));
					pw.println(String.format("    SetPanTarget(0, CamTarget_%s_x, CamTarget_%s_y, CamTarget_%s_z);", m.getName(), m.getName(), m.getName()));
				}
				else {
					CameraZoneData controlData = m.cameraComponent.controlData;
					pw.println(String.format("    SetCamType(0, %d, %s);",
						controlData.getType().index, controlData.getFlag() ? "TRUE" : "FALSE"));
					pw.println(String.format("    SetCamPitch(0, %f, %f);",
						controlData.boomPitch.get(), controlData.viewPitch.get()));
					pw.println(String.format("    SetCamDistance(%d);",
						Math.round(controlData.boomLength.get())));
					if (controlData.getType() == ControlType.TYPE_4) {
						pw.println(String.format("    SetCamPosA(0, %d, %d);",
							controlData.posA.getX(), controlData.posA.getZ())); // 0/2 - Ax/Az
						pw.println(String.format("    SetCamPosB(0, %d, %d);",
							controlData.posA.getY(), controlData.posB.getY())); // 3/5 - Bx/Bz
						pw.println(String.format("    SetCamPosC(0, %d, %d);",
							controlData.posB.getX(), controlData.posB.getZ())); // 1/4 - Ay/By
					}
					else {
						pw.println(String.format("    SetCamPosA(0, %d, %d);",
							controlData.posA.getX(), controlData.posC.getX())); // 0/2 - Ax/Az
						pw.println(String.format("    SetCamPosB(0, %d, %d);",
							controlData.posA.getZ(), controlData.posB.getX())); // 3/5 - Bx/Bz
						pw.println(String.format("    SetCamPosC(0, %d, %d);",
							controlData.posC.getZ(), controlData.posB.getZ())); // 1/4 - Ay/By
					}
					pw.println(String.format("    SetPanTarget(0, CamTarget_%s_x, CamTarget_%s_y, CamTarget_%s_z);", m.getName(), m.getName(), m.getName()));
				}

				if (m.cameraComponent.generatePan.get()) {
					pw.println(String.format("    SetCamSpeed(0, %f);", m.cameraComponent.moveSpeed.get()));
					pw.println("    PanToTarget(0, 0, 1);");
					pw.println("    WaitForCam(0, 1.0);");
				}

				pw.println("});");
				pw.println("");
			}
		}
	}
}
