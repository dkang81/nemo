import scala.swing._
import javax.swing.UIManager

object NemoContainer {
  def apply(t:NemoSheetView) = {
    val nc = new NemoContainer
    nc.loadNemo(t)
    nc
  }
}

class NemoContainer extends BoxPanel(Orientation.Vertical) {
  class BasicNemo(val t:NemoSheetView) extends ScrollPane(t) {
    val rh = new NemoRowHeader(t)
    rowHeaderView = rh
    t.rowHeader = rh
  }

  private var bNemo:BasicNemo = null
  def nemo = if (bNemo == null) null else bNemo.t
  def nemoIndex = contents.indexOf(bNemo)
  def loadNemo(t:NemoSheetView) {
    val i = nemoIndex
    if (i > -1) contents.remove(nemoIndex)
    bNemo = new BasicNemo(t)
    contents += bNemo
    revalidate
    repaint
  }

  contents += new FlowPanel {
    contents += Button("Undo") {
      nemo.sheetModel.undo
    }
    contents += Button("Redo") {
      nemo.sheetModel.redo
    }
    contents += Button("Print") {
      println(nemo.sheetModel.toNodeSeq)
    }
    contents += Button("Load Demo") {
      val demo = <nemotable rows="5" cols="5">
      <cell row="0" col="0" formula="5"/>
      <cell row="1" col="0" formula="10"/>
      <cell row="2" col="0" formula="a1+a2"/>
      </nemotable>
      println(demo)
      loadNemo(NemoSheetView(NemoSheetModel(demo)))
    }
    contents += Button("Open") {
      val d = new FileChooser
      val choice = d.showOpenDialog(null)
      if (choice == FileChooser.Result.Approve)
        NemoSheetModel.openFile(d.selectedFile).foreach(t2 => loadNemo(NemoSheetView(t2)))
    }        

    contents += Button("Save") {
      val d = new FileChooser
      val choice = d.showSaveDialog(null)
      if (choice == FileChooser.Result.Approve)
        NemoSheetModel.saveFile(nemo.sheetModel, d.selectedFile)
    }

    contents += Button("Script Editor") {
      val editor = new ScriptEditorWindow(Seq("Standard Library", "Your Nemoscript"),
                                          Seq(nemo.sheetModel.standardLib, nemo.sheetModel.customScript),
                                          nemo.sheetModel)
      editor.open
    }
    minimumSize = preferredSize
    maximumSize = preferredSize
  }
}

object NemoApp extends SimpleSwingApplication {
  def top = new MainFrame {
    //UIManager.setLookAndFeel("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")
    UIManager.setLookAndFeel("com.sun.java.swing.plaf.nimbus.NimbusLookAndFeel")
    //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    title = "NemoCalc"
    val nemo = NemoSheetView(NemoSheetModel(512,64))
    contents = NemoContainer(nemo)
    //contents = nemo
    centerOnScreen
  }
}

