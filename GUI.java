import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class GUI extends JFrame implements ActionListener{
	
	private final JTextArea textArea;
	private JScrollPane scrollPane;
	private final JTextField userInputField;
	private JComboBox combo;

	private Datastore state;
	
    public GUI(String[] args) throws IOException, InterruptedException {
		super("P2P Chat");
		
		//Redirect System out to TextArea
        redirectSystemStreams();
        
		this.setSize(400, 200);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	
		// We create a TextArea object
		textArea = new JTextArea(5, 50);
		
		// We put the TextArea object in a Scrollable Pane
		scrollPane = new JScrollPane(textArea);
	
		// Set Size
		scrollPane.setPreferredSize(new Dimension(380, 100));
	
		// Wrap Lines
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
	
		// No Edit
		textArea.setEditable(false);
	
		// Scroll Bar
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
	
		// Input Field
		userInputField = new JTextField(30);
		userInputField.addActionListener(this);
		
		String[] messageOptions = { "ALL" };

		//Create the combo box, select item at index 4.
		//Indices start at 0, so 4 specifies the pig.
		combo = new JComboBox(messageOptions);
	
		this.setLayout(new FlowLayout());

		//adds and centers the text field to the frame
		this.add(userInputField, SwingConstants.CENTER);
		//adds and centers the combo box to the frame
		this.add(combo, SwingConstants.CENTER);
		//adds and centers the scroll pane to the frame
		this.add(scrollPane, SwingConstants.CENTER);

		
		this.setResizable(true);
		this.setVisible(true);
		
		/*
		 * Do all our initializing stuff
		 */
    	state = new Datastore();
    	state.guiPeerList = combo;
    	
    	//Get information from user and connect to swarm
    	Config config = new Config(state);
        
    }
    
    public void actionPerformed(ActionEvent event){
		//We get the text from the textfield
    	if(event.getSource() == userInputField) {
			String userText = userInputField.getText();
			
			String userSelection = (String)combo.getSelectedItem();
			
			//Send a public message
			if(userSelection.equals("ALL")) {
				Message theMessage = new Message(state.self.toString()+",PUBLIC_MESSAGE,"+userText+",127.0.0.1/0000");
				state.sendQueue.offer(theMessage);
			}
			//Send a private message
			else {
				Message theMessage = new Message(state.self.toString()+",PRIVATE_MESSAGE,"+userText+","+userSelection);
				state.sendQueue.offer(theMessage);
			}
			
	
    	}
	}
    
    private void updateTextArea(final String text) {
    	textArea.append(text);
    	textArea.setCaretPosition(textArea.getDocument().getLength());
    }
    
    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            updateTextArea(String.valueOf((char) b));
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            updateTextArea(new String(b, off, len));
          }

          @Override
          public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
          }
        };

        System.setOut(new PrintStream(out, true));
      }


    public static void main(String[] args) throws IOException, InterruptedException {
    	
    	//Create Program
    	new GUI(args);
    	
    }
}