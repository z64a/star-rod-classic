package game.map.patching;

import game.map.Map;

public abstract class MapDecorator
{
	public static final void decorate(Map map)
	{
		// not actually needed anymore...
		switch (map.name) {
			case "dgb_00":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.overrideTex.set(true);
				map.scripts.shapeOverrideName.set("arn_20_shape");
				map.scripts.hitOverrideName.set("arn_20_hit");
				map.texName = "arn_tex";
				break;
			case "kpa_51":
			case "kpa_53":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("kpa_50_shape");
				map.scripts.hitOverrideName.set("kpa_50_hit");
			case "kpa_81":
			case "kpa_82":
			case "kpa_83":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("kpa_80_shape");
				map.scripts.hitOverrideName.set("kpa_80_hit");
				break;
			case "kpa_100":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("kpa_117_shape");
				map.scripts.hitOverrideName.set("kpa_117_hit");
				break;
			case "kpa_101":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("kpa_119_shape");
				map.scripts.hitOverrideName.set("kpa_119_hit");
				break;
			case "kpa_114":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("kpa_112_shape");
				map.scripts.hitOverrideName.set("kpa_112_hit");
				break;
			case "osr_04":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("osr_03_shape");
				map.scripts.hitOverrideName.set("osr_03_hit");
				break;
			case "pra_06":
			case "pra_12":
			case "pra_27":
			case "pra_28":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("pra_05_shape");
				map.scripts.hitOverrideName.set("pra_05_hit");
				break;
			case "pra_36":
			case "pra_37":
			case "pra_38":
			case "pra_39":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("pra_10_shape");
				map.scripts.hitOverrideName.set("pra_10_hit");
				break;
			case "tik_24":
				map.scripts.overrideShape.set(true);
				map.scripts.overrideHit.set(true);
				map.scripts.shapeOverrideName.set("tik_18_shape");
				map.scripts.hitOverrideName.set("tik_18_hit");
				break;
		}

		switch (map.name) {
			case "jan_11":
			case "kgr_01":
			case "kgr_02":
			case "kpa_01":
			case "kpa_03":
			case "omo_12":
			case "omo_14":
				map.scripts.isDark.set(true);
		}
	}
}
