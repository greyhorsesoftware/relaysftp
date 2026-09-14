package com.gideonsoftworks.gideon.logviewer;

import com.gideonsoftworks.gideon.core.GideonConfiguration;
import com.gideonsoftworks.gideon.core.GGideonPanelController;
import com.gideonsoftworks.gcore.*;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.LinkedBlockingQueue;

public class GLogPanelController extends GGideonPanelController {

    private RSyntaxTextArea     messagesText    =   null;
    private LinkedBlockingQueue<String> queue    =   null;
    private Thread              readerThread    =   null;
    private volatile boolean    running         =   false;

    public  GLogPanelController( GLog buf )
    {
        JPanel panel = new JPanel(new BorderLayout());

        if( GideonConfiguration.LAYOUT_DEBUG ) panel.setBorder(BorderFactory.createLineBorder(Color.black));

        messagesText = new RSyntaxTextArea();
        messagesText.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        messagesText.setEditable(false);
        messagesText.setCodeFoldingEnabled(false);
        messagesText.setAntiAliasingEnabled(true);
        messagesText.setHighlightCurrentLine(true);
        messagesText.setCurrentLineHighlightColor(new Color(220, 235, 252));
        messagesText.setFont(new Font("Menlo", Font.PLAIN, 12));

        RTextScrollPane scrollPane = new RTextScrollPane(messagesText);
        scrollPane.setLineNumbersEnabled(true);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add( scrollPane, BorderLayout.CENTER );

        setPanel(panel);

        if( buf != null ) setLog(buf);
    }

    public void setLog( GLog daLog )
    {
        stopMonitoring();

        queue = new LinkedBlockingQueue<>();
        daLog.setMessageQueue(queue);

        // Flush existing messages
        String existing = daLog.getNewMessageText();
        if( existing != null && !existing.isEmpty() )
        {
            SwingUtilities.invokeLater(() -> messagesText.append(existing));
        }

        // Start reader thread — blocks on queue.take(), no polling
        running = true;
        readerThread = Thread.ofVirtual().name("log-reader").start(() -> {
            StringBuilder batch = new StringBuilder();
            while( running )
            {
                try {
                    // Block until at least one message arrives
                    batch.append(queue.take());

                    // Drain any additional queued messages
                    String more;
                    while( (more = queue.poll()) != null )
                    {
                        batch.append(more);
                    }

                    String text = batch.toString();
                    batch.setLength(0);
                    SwingUtilities.invokeLater(() -> messagesText.append(text));

                } catch( InterruptedException e ) {
                    break;
                }
            }
        });
    }

    public void startMonitoring()
    {
        // Reader thread starts in setLog
    }

    public void stopMonitoring()
    {
        running = false;
        if( readerThread != null )
        {
            readerThread.interrupt();
            readerThread = null;
        }
        queue = null;
    }
}
