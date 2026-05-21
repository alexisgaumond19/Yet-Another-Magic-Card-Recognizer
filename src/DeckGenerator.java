import forohfor.scryfall.api.Card;
import forohfor.scryfall.api.MTGCardQuery;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class DeckGenerator extends JFrame
{

    private static final long serialVersionUID = 1L;
    private static ArrayList<String> ignore = new ArrayList<>();

    static
    {
        ignore.add("plains");
        ignore.add("island");
        ignore.add("swamp");
        ignore.add("mountain");
        ignore.add("forest");
    }

    private JTextArea jt;
    private JButton gen;
    private JTextField namebox;

    /**
     * Classe interne pour stocker un nom de carte avec un set code optionnel
     */
    private static class CardQuery
    {
        final String name;
        final String setCode;

        CardQuery(String name, String setCode)
        {
            this.name = name;
            this.setCode = setCode;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof CardQuery)
            {
                CardQuery other = (CardQuery) obj;
                return this.name.equals(other.name) && this.setCodeEquals(other.setCode);
            }
            return false;
        }

        private boolean setCodeEquals(String otherSetCode)
        {
            if (this.setCode == null && otherSetCode == null)
                return true;
            if (this.setCode == null || otherSetCode == null)
                return false;
            return this.setCode.equals(otherSetCode);
        }
    }

    public ArrayList<CardQuery> getCardNames()
    {
        String decklist = jt.getText();
        ArrayList<CardQuery> added = new ArrayList<>();

        for (String cardname : decklist.split("\n"))
        {
            cardname = cardname.trim();
            if (cardname.startsWith("SB:"))
            {
                cardname = cardname.replace("SB:", "");
                cardname = cardname.trim();
            }

            cardname = removeLeadingNumber(cardname);
            if (cardname.contains("\t"))
            {
                cardname = cardname.split("\t")[1];
            }

            String setCode = null;
            // Extraire le set code des parenthèses (ex: "Sol Ring (RNA)")
            if (cardname.matches(".*\\s*\\([A-Z0-9]{1,3}\\)\\s*"))
            {
                int lastParen = cardname.lastIndexOf('(');
                if (lastParen != -1)
                {
                    int closeParen = cardname.indexOf(')', lastParen);
                    if (closeParen != -1)
                    {
                        setCode = cardname.substring(lastParen + 1, closeParen).trim();
                        cardname = cardname.substring(0, lastParen).trim();
                    }
                }
            }

            if (!cardname.isEmpty() && !ignore.contains(cardname.toLowerCase()))
            {
                CardQuery cq = new CardQuery(cardname, setCode);
                if (!added.contains(cq))
                {
                    added.add(cq);
                }
            }
        }
        return added;
    }

    public static String removeLeadingNumber(String line)
    {
        int lastNum = 0;
        while (lastNum < line.length() && Character.isDigit(line.charAt(lastNum)))
        {
            lastNum++;
        }
        return line.substring(lastNum).trim();
    }

    public DeckGenerator()
    {
        super("Deck generator");
        gen = new JButton("Generate Deck");
        namebox = new JTextField("Enter Deck Name");
        JScrollPane scroll = new JScrollPane();
        gen.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                gen.setEnabled(false);
                writeDeck(SavedConfig.PATH);
                gen.setEnabled(true);
            }
        });

        JPanel bot = new JPanel();
        bot.setLayout(new BorderLayout());

        jt = new JTextArea(10, 50);
        jt.setText("Paste Decklist Here");
        scroll.setViewportView(jt);
        setLayout(new BorderLayout());
        add(scroll, BorderLayout.CENTER);
        bot.add(namebox, BorderLayout.CENTER);
        bot.add(gen, BorderLayout.SOUTH);
        add(bot, BorderLayout.SOUTH);
        pack();
        setVisible(true);
    }

    public void writeDeck(String path)
    {
        ListRecogStrat r = new ListRecogStrat(namebox.getText());
        new File(SavedConfig.getSubPath("decks")).mkdirs();
        File f = new File(SavedConfig.getCustomSetPath("decks", namebox.getText()));

        ArrayList<CardQuery> cardQueries = getCardNames();
        ArrayList<Card> cards = new ArrayList<>();

        // Convertir les CardQuery en noms pour la recherche initiale
        ArrayList<String> names = new ArrayList<>();
        for (CardQuery cq : cardQueries)
        {
            names.add(cq.name);
        }

        // Obtenir toutes les versions de chaque carte
        ArrayList<Card> allCards = MTGCardQuery.toCardList(names, true);

        // Filtrer par set code si spécifié
        for (CardQuery cq : cardQueries)
        {
            for (Card card : allCards)
            {
                if (card.getName().equalsIgnoreCase(cq.name))
                {
                    // Si un set code est spécifié, vérifier qu'il correspond
                    if (cq.setCode != null && !cq.setCode.isEmpty())
                    {
                        if (card.getSetCode().equalsIgnoreCase(cq.setCode))
                        {
                            cards.add(card);
                            break; // Arrêter à la première correspondance exacte
                        }
                    }
                    else
                    {
                        // Pas de set code spécifié, ajouter la carte
                        cards.add(card);
                        break; // Prendre la première version trouvée
                    }
                }
            }
        }

        final OperationBar bar = RecogApp.INSTANCE.getOpBar();
        if (bar.setTask("Generating Deck...", cards.size()))
        {
            new Thread()
            {
                public void run()
                {
                    for (Card card : cards)
                    {
                        bar.setSubtaskName(String.format("%s (%s)", card.getName(), card.getSetCode()));
                        r.addFromCard(card);
                        bar.progressTask();
                    }
                    try
                    {
                        r.writeOut(f);
                        JOptionPane.showMessageDialog(null,
                                "Deck saved with " + r.size() + " unique cards from " + names.size() + " card names.",
                                "Deck Saved", JOptionPane.INFORMATION_MESSAGE, null);
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                        JOptionPane.showMessageDialog(null,
                                "Deck couldn't be saved",
                                "Error", JOptionPane.ERROR_MESSAGE, null);
                    }
                }
            }.start();
        }
    }

}
