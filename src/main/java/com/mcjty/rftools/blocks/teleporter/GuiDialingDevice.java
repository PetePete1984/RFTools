package com.mcjty.rftools.blocks.teleporter;

import com.mcjty.container.EmptyContainer;
import com.mcjty.container.GenericGuiContainer;
import com.mcjty.gui.events.ButtonEvent;
import com.mcjty.gui.events.DefaultSelectionEvent;
import com.mcjty.gui.layout.HorizontalAlignment;
import com.mcjty.gui.layout.HorizontalLayout;
import com.mcjty.gui.layout.VerticalLayout;
import com.mcjty.gui.widgets.Button;
import com.mcjty.gui.widgets.*;
import com.mcjty.gui.widgets.Label;
import com.mcjty.gui.widgets.Panel;
import com.mcjty.rftools.RFTools;
import com.mcjty.rftools.blocks.storagemonitor.StorageScannerConfiguration;
import com.mcjty.rftools.network.Argument;
import com.mcjty.rftools.network.PacketHandler;
import com.mcjty.rftools.network.PacketRequestIntegerFromServer;
import com.mcjty.varia.Coordinate;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import org.lwjgl.input.Keyboard;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GuiDialingDevice extends GenericGuiContainer<DialingDeviceTileEntity> {

    public static final int DIALER_WIDTH = 256;
    public static final int DIALER_HEIGHT = 224;

    private static final ResourceLocation iconDialOn = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private EnergyBar energyBar;
    private WidgetList transmitterList;
    private WidgetList receiverList;
    private Button dialButton;
    private Button interruptButton;
    private Button statusButton;
    private Label statusLabel;

    private boolean analyzerAvailable = false;
    private boolean lastDialedTransmitter = false;
    private boolean lastCheckedReceiver = false;

    public static int fromServer_receiverStatus = -1;
    public static int fromServer_dialResult = -1;
    public static List<TeleportDestinationClientInfo> fromServer_receivers = null;
    public static List<TransmitterInfo> fromServer_transmitters = null;

    // A copy of the receivers we're currently showing.
    private List<TeleportDestinationClientInfo> receivers = null;

    // A copy of the transmitters we're currently showing.
    private List<TransmitterInfo> transmitters = null;

    private int listDirty = 0;


    public GuiDialingDevice(DialingDeviceTileEntity dialingDeviceTileEntity, EmptyContainer container) {
        super(dialingDeviceTileEntity, container);
        dialingDeviceTileEntity.setCurrentRF(dialingDeviceTileEntity.getEnergyStored(ForgeDirection.DOWN));

        xSize = DIALER_WIDTH;
        ySize = DIALER_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        int k = (this.width - DIALER_WIDTH) / 2;
        int l = (this.height - DIALER_HEIGHT) / 2;

        int maxEnergyStored = tileEntity.getMaxEnergyStored(ForgeDirection.DOWN);
        energyBar = new EnergyBar(mc, this).setFilledRectThickness(1).setHorizontal().setDesiredWidth(80).setDesiredHeight(12).setMaxValue(maxEnergyStored).setShowText(true);
        energyBar.setValue(tileEntity.getCurrentRF());

        Panel transmitterPanel = setupTransmitterPanel();
        Panel receiverPanel = setupReceiverPanel();

        dialButton = new Button(mc, this).setText("Dial").setTooltips("Start a connection between", "the selected transmitter", "and the selected receiver").
                setDesiredHeight(14).
                addButtonEvent(new ButtonEvent() {
                    @Override
                    public void buttonClicked(Widget parent) {
                        dial();
                    }
                });
        interruptButton = new Button(mc, this).setText("Interrupt").setTooltips("Interrupt a connection", "for the selected transmitter").
                setDesiredHeight(14).
                addButtonEvent(new ButtonEvent() {
                    @Override
                    public void buttonClicked(Widget parent) {
                        interruptDial();
                    }
                });

        analyzerAvailable =  DialingDeviceTileEntity.isDestinationAnalyzerAvailable(mc.theWorld, tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        statusButton = new Button(mc, this).setText("Check").
                setDesiredHeight(14).
                setEnabled(analyzerAvailable).
                addButtonEvent(new ButtonEvent() {
                    @Override
                    public void buttonClicked(Widget parent) {
                        checkStatus();
                    }
                });
        if (analyzerAvailable) {
            statusButton.setTooltips("Check the status of", "the selected receiver");
        } else {
            statusButton.setTooltips("Check the status of", "the selected receiver", "(needs an adjacent analyzer!)");
        }

        Panel buttonPanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(dialButton).addChild(interruptButton).addChild(statusButton).setDesiredHeight(16);

        statusLabel = new Label(mc, this);
        statusLabel.setDesiredWidth(180).setDesiredHeight(14).setFilledRectThickness(1);
        Panel statusPanel = new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(new Label(mc, this).setText("Status")).addChild(statusLabel).setDesiredHeight(16);

        Widget toplevel = new Panel(mc, this).setFilledRectThickness(2).setLayout(new VerticalLayout()).
                addChild(energyBar).addChild(transmitterPanel).
                addChild(receiverPanel).addChild(buttonPanel).addChild(statusPanel);
        toplevel.setBounds(new Rectangle(k, l, DIALER_WIDTH, DIALER_HEIGHT));
        window = new com.mcjty.gui.Window(this, toplevel);
        Keyboard.enableRepeatEvents(true);

        listDirty = 0;
        clearSelectedStatus();

        requestReceivers();
        requestTransmitters();
        tileEntity.requestRfFromServer();
    }

    private Panel setupReceiverPanel() {
        receiverList = new WidgetList(mc, this).setRowheight(14).setFilledRectThickness(1).addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void select(Widget parent, int index) {
                clearSelectedStatus();
            }

            @Override
            public void doubleClick(Widget parent, int index) {
                hilightSelectedReceiver(index);
            }
        });
        Slider receiverSlider = new Slider(mc, this).setDesiredWidth(13).setVertical().setScrollable(receiverList);
        return new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(receiverList).addChild(receiverSlider);
    }

    private Panel setupTransmitterPanel() {
        transmitterList = new WidgetList(mc, this).setRowheight(18).setFilledRectThickness(1).setDesiredHeight(76).addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void select(Widget parent, int index) {
                clearSelectedStatus();
                selectReceiverFromTransmitter();
            }

            @Override
            public void doubleClick(Widget parent, int index) {
                hilightSelectedTransmitter(index);
            }
        });
        Slider transmitterSlider = new Slider(mc, this).setDesiredWidth(13).setVertical().setScrollable(transmitterList);
        return new Panel(mc, this).setLayout(new HorizontalLayout()).addChild(transmitterList).addChild(transmitterSlider);
    }

    private void clearSelectedStatus() {
        lastDialedTransmitter = false;
        lastCheckedReceiver = false;
    }

    private void hilightSelectedTransmitter(int index) {
        if (index == -1) {
            return;
        }
        TransmitterInfo transmitterInfo = transmitters.get(index);
        Coordinate c = transmitterInfo.getCoordinate();
        RFTools.instance.clientInfo.hilightBlock(c, System.currentTimeMillis()+1000* StorageScannerConfiguration.hilightTime);
        mc.getMinecraft().thePlayer.closeScreen();
    }

    private void hilightSelectedReceiver(int index) {
        if (index == -1) {
            return;
        }
        TeleportDestination destination = receivers.get(index);

        Coordinate c = destination.getCoordinate();
        double distance = Vec3.createVectorHelper(c.getX(), c.getY(), c.getZ()).distanceTo(mc.thePlayer.getPosition(1.0f));

        if (destination.getDimension() != mc.theWorld.provider.dimensionId || distance > 150) {
            RFTools.warn(mc.thePlayer, "Receiver is too far to hilight!");
            mc.getMinecraft().thePlayer.closeScreen();
            return;
        }
        RFTools.instance.clientInfo.hilightBlock(c, System.currentTimeMillis()+1000* StorageScannerConfiguration.hilightTime);
        RFTools.message(mc.thePlayer, "The receiver is now hilighted");
        mc.getMinecraft().thePlayer.closeScreen();
    }

    private void setStatusError(String message) {
        statusLabel.setText(message);
        statusLabel.setColor(0xffff0000);
    }

    private void setStatusMessage(String message) {
        statusLabel.setText(message);
        statusLabel.setColor(0xff000000);
    }

    private void checkStatus() {
        int receiverSelected = receiverList.getSelected();
        if (receiverSelected == -1) {
            return; // Shouldn't happen. Just to be sure.
        }
        TeleportDestination destination = receivers.get(receiverSelected);
        Coordinate c = destination.getCoordinate();
        PacketHandler.INSTANCE.sendToServer(new PacketRequestIntegerFromServer(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord,
                DialingDeviceTileEntity.CMD_CHECKSTATUS,
                DialingDeviceTileEntity.CLIENTCMD_STATUS, new Argument("c", c), new Argument("dim", destination.getDimension())));

        lastCheckedReceiver = true;
        listDirty = 0;
    }

    private void showStatus(int dialResult) {
        if ((dialResult & DialingDeviceTileEntity.DIAL_DIALER_POWER_LOW_MASK) != 0) {
            setStatusError("Dialing device power low!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_RECEIVER_POWER_LOW_MASK) != 0) {
            setStatusError("Matter receiver power low!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_DIMENSION_POWER_LOW_MASK) != 0) {
            setStatusError("Destination dimension power low!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_TRANSMITTER_NOACCESS) != 0) {
            setStatusError("No access to transmitter!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_RECEIVER_NOACCESS) != 0) {
            setStatusError("No access to receiver!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_INVALID_DESTINATION_MASK) != 0) {
            setStatusError("Invalid destination!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_INVALID_SOURCE_MASK) != 0) {
            setStatusError("Invalid source!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_RECEIVER_BLOCKED_MASK) != 0) {
            setStatusError("Receiver blocked!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_TRANSMITTER_BLOCKED_MASK) != 0) {
            setStatusError("Transmitter blocked!");
            return;
        }
        if ((dialResult & DialingDeviceTileEntity.DIAL_INTERRUPTED) != 0) {
            setStatusMessage("Interrupted!");
            return;
        }
        setStatusMessage("Dial ok!");
    }

    private void selectReceiverFromTransmitter() {
        receiverList.setSelected(-1);
        TeleportDestination destination = getSelectedTransmitterDestination();
        if (destination == null) {
            return;
        }
        int i = 0;
        for (TeleportDestination receiver : receivers) {
            if (receiver.getDimension() == destination.getDimension() && receiver.getCoordinate().equals(destination.getCoordinate())) {
                receiverList.setSelected(i);
                return;
            }
            i++;
        }
    }

    private void dial() {
        int transmitterSelected = transmitterList.getSelected();
        int receiverSelected = receiverList.getSelected();
        if (transmitterSelected == -1 || receiverSelected == -1) {
            return; // Shouldn't happen. Just to be sure.
        }
        TransmitterInfo transmitterInfo = transmitters.get(transmitterSelected);
        TeleportDestination destination = receivers.get(receiverSelected);

        PacketHandler.INSTANCE.sendToServer(new PacketRequestIntegerFromServer(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, DialingDeviceTileEntity.CMD_DIAL,
                DialingDeviceTileEntity.CLIENTCMD_DIAL,
                new Argument("player", mc.thePlayer.getDisplayName()),
                new Argument("trans", transmitterInfo.getCoordinate()), new Argument("transDim", mc.theWorld.provider.dimensionId),
                new Argument("c", destination.getCoordinate()), new Argument("dim", destination.getDimension())));

        lastDialedTransmitter = true;
        listDirty = 0;
    }

    private void interruptDial() {
        int transmitterSelected = transmitterList.getSelected();
        if (transmitterSelected == -1) {
            return; // Shouldn't happen. Just to be sure.
        }
        TransmitterInfo transmitterInfo = transmitters.get(transmitterSelected);
        PacketHandler.INSTANCE.sendToServer(new PacketRequestIntegerFromServer(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, DialingDeviceTileEntity.CMD_DIAL,
                DialingDeviceTileEntity.CLIENTCMD_DIAL,
                new Argument("player", mc.thePlayer.getDisplayName()),
                new Argument("trans", transmitterInfo.getCoordinate()), new Argument("transDim", mc.theWorld.provider.dimensionId),
                new Argument("c", (Coordinate) null), new Argument("dim", 0)));

        lastDialedTransmitter = true;
        listDirty = 0;
    }

    private void requestReceivers() {
        PacketHandler.INSTANCE.sendToServer(new PacketGetReceivers(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord, mc.thePlayer.getDisplayName()));
    }

    private void requestTransmitters() {
        PacketHandler.INSTANCE.sendToServer(new PacketGetTransmitters(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord));
    }

    private void populateReceivers() {
        List<TeleportDestinationClientInfo> newReceivers = fromServer_receivers;
        if (newReceivers == null) {
            return;
        }
        if (newReceivers.equals(receivers)) {
            return;
        }

        receivers = new ArrayList<TeleportDestinationClientInfo>(newReceivers);
        receiverList.removeChildren();

        for (TeleportDestinationClientInfo destination : receivers) {
            Coordinate coordinate = destination.getCoordinate();

            String dimName = destination.getDimensionName();
            if (dimName == null || dimName.trim().isEmpty()) {
                dimName = "Id " + destination.getDimension();
            }

            Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout());
            panel.addChild(new Label(mc, this).setText(destination.getName()).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(65));
            panel.addChild(new Label(mc, this).setDynamic(true).setText(coordinate.toString()));
            panel.addChild(new Label(mc, this).setText(dimName).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(55));
            receiverList.addChild(panel);
        }
    }

    private TeleportDestination getSelectedTransmitterDestination() {
        int transmitterSelected = transmitterList.getSelected();
        if (transmitterSelected == -1) {
            return null;
        }
        TransmitterInfo transmitterInfo = transmitters.get(transmitterSelected);
        TeleportDestination destination = transmitterInfo.getTeleportDestination();
        if (destination.isValid()) {
            return destination;
        } else {
            return null;
        }
    }

    private void populateTransmitters() {
        List<TransmitterInfo> newTransmitters = fromServer_transmitters;
        if (newTransmitters == null) {
            return;
        }
        if (newTransmitters.equals(transmitters)) {
            return;
        }

        transmitters = new ArrayList<TransmitterInfo>(newTransmitters);
        transmitterList.removeChildren();

        for (TransmitterInfo transmitterInfo : transmitters) {
            Coordinate coordinate = transmitterInfo.getCoordinate();
            TeleportDestination destination = transmitterInfo.getTeleportDestination();

            Panel panel = new Panel(mc, this).setLayout(new HorizontalLayout());
            panel.addChild(new Label(mc, this).setText(transmitterInfo.getName()).setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT).setDesiredWidth(90));
            panel.addChild(new Label(mc, this).setDynamic(true).setText(coordinate.toString()));
            panel.addChild(new ImageLabel(mc, this).setImage(iconDialOn, destination.isValid() ? 80 : 96, 0).setDesiredWidth(16));
            transmitterList.addChild(panel);
        }
    }


    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        requestListsIfNeeded();

        populateReceivers();
        populateTransmitters();

        if (lastDialedTransmitter) {
            showStatus(fromServer_dialResult);
        } else if (lastCheckedReceiver) {
            showStatus(fromServer_receiverStatus);
        } else {
            statusLabel.setText("");
        }

        enableButtons();

        window.draw();
        int currentRF = tileEntity.getCurrentRF();
        energyBar.setValue(currentRF);
        tileEntity.requestRfFromServer();
    }

    private void requestListsIfNeeded() {
        listDirty--;
        if (listDirty <= 0) {
            requestReceivers();
            requestTransmitters();
            listDirty = 20;
        }
    }

    private String calculateDistance(int transmitterSelected, int receiverSelected) {
        TransmitterInfo transmitterInfo = transmitters.get(transmitterSelected);
        TeleportDestination teleportDestination = receivers.get(receiverSelected);

        return DialingDeviceTileEntity.calculateDistance(mc.theWorld, transmitterInfo, teleportDestination);
    }

    private void enableButtons() {
        int transmitterSelected = transmitterList.getSelected();
        int receiverSelected = receiverList.getSelected();
        if (transmitterSelected != -1 && receiverSelected != -1) {
            dialButton.setEnabled(true);
            String distance = calculateDistance(transmitterSelected, receiverSelected);
            dialButton.setTooltips("Start a connection between", "the selected transmitter", "and the selected receiver.", "Distance: "+distance);
        } else {
            dialButton.setEnabled(false);
            dialButton.setTooltips("Start a connection between", "the selected transmitter", "and the selected receiver");
        }
        if (transmitterSelected != -1) {
            TeleportDestination destination = getSelectedTransmitterDestination();
            interruptButton.setEnabled(destination != null);
        } else {
            interruptButton.setEnabled(false);
        }
        if (receiverSelected != -1) {
            statusButton.setEnabled(analyzerAvailable);
        } else {
            statusButton.setEnabled(false);
        }
    }
}
