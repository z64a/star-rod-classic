package game.globals.editor;

import app.Environment;
import app.StarRodException;
import app.SwingUtils;
import app.input.IOUtils;
import game.globals.editor.renderers.MessageCellRenderer;
import game.string.PMString;
import game.string.editor.io.StringResource;
import util.IterableListModel;
import util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static app.Directories.MOD_STRINGS_PATCH;
import static app.Directories.MOD_STRINGS_SRC;

public class GameStrings {

    public final IterableListModel<PMString> messageListModel = new IterableListModel<>();
    public final Map<String, PMString> messageNameMap = new HashMap<>();
    public final Map<Integer, PMString> messageIDMap = new HashMap<>();

    public GameStrings()
    {
    }

    public void loadStrings()
    {
        try {
            if (Environment.project.isDecomp) {
                for (File assetDir : Environment.project.decompConfig.assetDirectories) {
                    File msgDir = new File(assetDir, "msg");
                    if (!msgDir.exists())
                        continue;

                    loadMessages(IOUtils.getFilesWithExtension(assetDir, new String[] { "msg" }, true));
                }
            }
            else {
                loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_SRC, new String[] { "str", "msg" }, true));
                loadMessages(IOUtils.getFilesWithExtension(MOD_STRINGS_PATCH, new String[] { "str", "msg" }, true));
            }
        }
        catch (IOException e) {
            throw new StarRodException("Exception while loading strings! %n%s", e.getMessage());
        }

        Logger.logf("Loaded %d strings", messageListModel.getSize());
    }

    private void loadMessages(Collection<File> msgFiles)
    {
        for (File f : msgFiles) {
            StringResource res = new StringResource(f);
            for (PMString str : res.strings) {
                if (str.hasName()) {
                    messageNameMap.put(str.name, str);
                    messageListModel.addElement(str);
                }
                else if (str.indexed && !str.autoAssign) {
                    messageNameMap.put(str.getIDName(), str);
                    messageIDMap.put(str.getID(), str);
                    messageListModel.addElement(str);
                }
            }
        }
    }

    public PMString chooseMessage(String title)
    {
        ListSelectorDialog<PMString> chooser = new ListSelectorDialog<>(messageListModel, new MessageCellRenderer(48));

        SwingUtils.showModalDialog(chooser, title);
        if (!chooser.isResultAccepted())
            return null;

        return chooser.getValue();
    }

    public PMString getMessage(String msgName)
    {
        if (msgName == null)
            return null;

        if (messageNameMap.containsKey(msgName))
            return messageNameMap.get(msgName);

        if (msgName.matches("[0-9A-Fa-f]{1,4}-[0-9A-Fa-f]{1,4}")) {
            String[] parts = msgName.split("-");
            int group = Integer.parseInt(parts[0], 16);
            int index = Integer.parseInt(parts[1], 16);
            int fullID = (group << 16) | (index & 0xFFFF);
            return messageIDMap.get(fullID);
        }

        if (msgName.matches("[0-9A-Fa-f]{1,8}"))
            return messageIDMap.get((int) Long.parseLong(msgName, 16));

        return null;
    }

}
