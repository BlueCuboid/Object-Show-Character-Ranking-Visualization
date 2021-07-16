import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
public class Ranker {
    private static class Play {
        String player;
        double scr;
        public Play(String p, double s) {
            player=p;
            scr=s;
        }
        public String toString() {
            return player+":"+scr;
        }
    }
    private static class Round {
        ArrayList<Play> plays;
        String name, episode;
        public Round(String name, String episode, ArrayList<Play> plays) {
            this.name=name;
            this.episode=episode;
            this.plays=new ArrayList<>(plays);
        }
    }
    private static HashMap<String,Integer> colors(String file) {
        Scanner sc;
        try {
            sc = new Scanner(new File(file));
        }
        catch (Exception e) {
            System.err.print("FILE NOT FOUND");
            return new HashMap<>();
        }
        HashMap<String,Integer> out=new HashMap<>();
        while (sc.hasNext()) {
            String line=sc.nextLine();
            int split=line.lastIndexOf(" ");
            out.put(line.substring(0,split),Integer.parseInt(line.substring(split+1),16));
        }
        return out;
    }
    private static boolean prefix(String pre, String str) {
        return str.length() >=pre.length() && str.substring(0,pre.length()).equals(pre);
    }
    private static ArrayList<Round> rounds(String data) {
        Scanner sc;
        try {
            sc = new Scanner(new File(data));
        }
        catch (Exception e) {
            System.err.print("FILE NOT FOUND");
            return new ArrayList<>();
        }
        ArrayList<Round> out=new ArrayList<>();
        ArrayList<Play> plays=new ArrayList<>();
        String round_name="", ep="";
        double scr=Double.POSITIVE_INFINITY;
        boolean names=false;
        while (sc.hasNext()) {
            String line=sc.nextLine();
            if (line.length()==0 || prefix("//",line))
                continue;
            if (prefix("%",line)) {
                names = true;
                continue;
            }
            if (names) {
                int split=line.indexOf(" ");
                String nick=line.substring(0,split),
                        real_name=line.substring(split+1);
                for (Round rnd:out)
                    for (Play p:rnd.plays)
                        if (p.player.equals(nick))
                            p.player=real_name;
                continue;
            }
            if (prefix("#",line) || prefix("ep",line) || prefix("e-",line) || prefix("!e-",line)) {
                if (plays.size() > 0)
                    out.add(new Round(round_name,ep,plays));
                plays.clear();
                round_name=line.substring(1);
                if (prefix("ep",line)) {
                    ep=line.substring(2);
                    continue;
                }
                if (prefix("e-",line)) {
                    //player is eliminated
                    ArrayList<Play> elims=new ArrayList<>();
                    elims.add(new Play(line.substring(2),Double.NEGATIVE_INFINITY));
                    out.add(new Round("elimination",ep,elims));
                    continue;
                }
                if (prefix("!e-",line)) {
                    //player rejoins
                    ArrayList<Play> elims=new ArrayList<>();
                    elims.add(new Play(line.substring(3),Double.POSITIVE_INFINITY));
                    out.add(new Round("rejoin",ep,elims));
                    continue;
                }
                continue;
            }
            try {
                scr = Double.parseDouble(line);
            } catch (Exception e) {
                //not a number: line is the name of a player
                plays.add(new Play(line, scr));
            }
        }
        if (plays.size()>0)
            out.add(new Round(round_name,ep,plays));
        return out;
    }
    private static class Vis extends JPanel {
        private static final int XMIN=70, XMAX=1200, YMIN=100, YMAX=740;
        private ArrayList<Play> activeRatings;
        private HashMap<String,Integer> orank;
        private HashMap<String,Double> orate, drate, drank;
        HashMap<String,Integer> elim_status;
        private String episode;
        private double dplayercnt;
        double alpha, maxRating, minRating;
        public boolean startcard;
        HashMap<String,Integer> colors;
        public Vis(HashMap<String,Integer> colors) {
            activeRatings=new ArrayList<>();
            orank=new HashMap<>();
            orate=new HashMap<>();
            drank=new HashMap<>();
            drate=new HashMap<>();
            elim_status=new HashMap<>();
            this.colors=colors;
            startcard=true;
        }
        public void paintRatings(Ratings r, String ep, long millis) {
            episode=ep;
            activeRatings=r.activePlayers();
            HashMap<String,Integer> nrank=new HashMap<>();
            HashMap<String,Double> nrate=new HashMap<>();
            drank.clear();
            drate.clear();
            elim_status.clear();
            for (int i=0; i<activeRatings.size(); i++) {
                Play p=activeRatings.get(i);
                nrank.put(p.player,i);
                nrate.put(p.player,p.scr);
            }
            double omaxRating=Double.NEGATIVE_INFINITY;
            for (double t:orate.values())
                omaxRating=Math.max(omaxRating,t);
            if (orate.keySet().size()==0)
                omaxRating=0;
            double ominRating=Double.POSITIVE_INFINITY;
            for (double t:orate.values())
                ominRating=Math.min(ominRating,t);
            if (orate.keySet().size()==0)
                ominRating=0;
            double nmaxRating=Double.NEGATIVE_INFINITY;
            for (double t:nrate.values())
                nmaxRating=Math.max(nmaxRating,t);
            double nminRating=Double.POSITIVE_INFINITY;
            for (double t:nrate.values())
                nminRating=Math.min(nminRating,t);
            long time=System.currentTimeMillis(),
                    tend=time+millis;
            while (true) {
                time=System.currentTimeMillis();
                if (time>tend)
                    break;
                alpha=1-(double)(tend-time)/millis;
                for (String player:orank.keySet()) {
                    int or=orank.get(player), nr;
                    double ot=orate.get(player), nt;
                    if (nrank.containsKey(player)) {
                        nr=nrank.get(player);
                        nt=nrate.get(player);
                    }
                    else {
                        nr=nrank.size()+1;
                        nt=ot;
                        elim_status.put(player,-1);
                    }
                    drank.put(player,or+(nr-or)*alpha);
                    drate.put(player,ot+(nt-ot)*alpha);
                }
                for (String player:nrank.keySet()) {
                    int nr=nrank.get(player);
                    double nt=nrate.get(player);
                    if (!orank.containsKey(player)) {
                        int or=orank.size()+1;
                        double ot=0;
                        drank.put(player,or+(nr-or)*alpha);
                        drate.put(player,ot+(nt-ot)*alpha);
                        elim_status.put(player,1);
                    }
                }
                dplayercnt=orate.size()+(nrate.size()-orate.size())*alpha;
                maxRating=20+omaxRating+(nmaxRating-omaxRating)*alpha;
                minRating=-100+ominRating+(nminRating-ominRating)*alpha;
                repaint();
            }
            orank=nrank;
            orate=nrate;
            drank.clear();
            for (String player:nrank.keySet())
                drank.put(player,nrank.get(player)+0.0);
            drate.clear();
            for (String player:nrate.keySet())
                drate.put(player,nrate.get(player));
            dplayercnt=nrate.size();
            maxRating=20+nmaxRating;
            minRating=-100+nminRating;
            repaint();
        }
        public void paintComponent(Graphics g) {
            g.setColor(Color.BLACK);
            g.fillRect(0,0,1400,1400);
            if (startcard) {
                g.setColor(Color.WHITE);
                g.setFont(new Font("Verdana", Font.PLAIN, 100));
                g.drawString("BFDI Season 1", 300,200);
                g.setFont(new Font("Verdana", Font.PLAIN, 72));
                g.drawString("Elo Rankings", 450,300);
                return;
            }
            double cnt=dplayercnt;
            int width=XMAX-XMIN, height=YMAX-YMIN;
            //draw scale
            int MSPACE=100;
            g.setFont(new Font("Helvetica", Font.PLAIN, 24));
            for (int m=(int)(Math.ceil(minRating/MSPACE)*MSPACE); m<=maxRating; m+=MSPACE) {
                g.setColor(Color.GRAY);
                int xloc=XMIN+(int)((m-minRating)/(maxRating-minRating)*width);
                g.fillRect(xloc,YMIN-20,3,height+20);
                g.drawString(""+m,xloc,YMIN-20);
            }
            for (String name:drate.keySet()) {
                double rating, i;
                try {
                    rating=drate.get(name);
                    i=drank.get(name);
                }
                catch (Exception e) {
                    System.err.println(e);
                    System.out.println(name);
                    System.out.println(drank);
                    System.out.println(drate);
                    continue;
                }
                double fade=elim_status.containsKey(name)?(elim_status.get(name)==-1?1-alpha:alpha):1;
                int colcode=colors.get(name);
                g.setColor(new Color((colcode >> 16), (colcode >> 8) & 0xff, colcode & 0xff, (int)(255*fade)));
                int rectw = (int) ((rating-minRating) / (maxRating-minRating) * width);
                g.fillRect(XMIN, (int) (YMIN + i * (double) height / cnt), rectw, (int) (height / cnt - 5));
                g.drawString("" + (int) rating, XMIN + rectw + 10, (int) (YMIN + i * (double) height / cnt + (height / cnt - 5) - 5));
                g.setColor(new Color(0,0,0, (int)(255*fade)));
                FontMetrics metrics = g.getFontMetrics(g.getFont());
                g.drawString(name, XMIN+rectw-metrics.stringWidth(name)-10, (int) (YMIN + i * (double) height / cnt + (height / cnt - 5) - 5));
            }
            g.setColor(Color.WHITE);
            for (int rank=0; rank<cnt; rank++) {
                int displayed=rank+1, rem=displayed%10;
                g.drawString(displayed+(rem==1?"st":rem==2?"nd":rem==3?"rd":"th"),XMIN-60, (int) (YMIN + rank * (double) height / cnt + (height / cnt - 5) - 5));
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font("Helvetica", Font.PLAIN, 48));
            g.drawString("Episode "+ episode,500,50);
            g.setColor(Color.BLACK);
            g.fillRect(0,YMAX,1400,300);
        }
    }
    public static void main(String[] args) {
        ArrayList<Round> rounds= rounds("BFDI\\s1.txt");
        Ratings bfdi=new Ratings();
        JFrame w = new JFrame("BFDI Season 1 Rankings");
        w.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container c = w.getContentPane();
        Vis visual=new Vis(colors("BFDI\\colors.txt"));
        c.add(visual);
        w.setBounds(0,0,1400,700);
        w.setVisible(true);
        waitms(7000);
        visual.startcard=false;
        visual.repaint();
        for (int i=0; i<rounds.size();) {
            String ep=rounds.get(i).episode;
            while (i<rounds.size() && rounds.get(i).episode.equals(ep)) {
                bfdi.update(rounds.get(i).plays);
                i++;
            }
            waitms(3000);
            visual.paintRatings(bfdi, ep, 2000);
        }
    }
    private static void waitms(long ms) {
        long TOUT=System.currentTimeMillis()+ms;
        while (System.currentTimeMillis()<TOUT) {}
    }
    private static class Ratings {
        //a list of players with their Elo ratings
        private HashMap<String,Double> ratings;
        private HashSet<String> elims;
        private static final double KVAL=8; //K-factor
        public static double winprob(double ra, double rb) {
            //probability of player with rating ra beating another player with rating rb
            return 1/(1+Math.pow(10,(rb-ra)/400));
        }
        public Ratings() {
            ratings=new HashMap<>();
            elims =new HashSet<>();
        }
        public String toString() {
            StringBuilder out=new StringBuilder();
            for (int type=0; type<2; type++) {
                out.append((type==0?"Playing:":"Eliminated:")+"\n");
                ArrayList<Play> ret = new ArrayList<>();
                for (String p : ratings.keySet())
                    if ((type==0)==!elims.contains(p))
                        ret.add(new Play(p, ratings.get(p)));
                ret.sort(
                        new Comparator<Play>() {
                            public int compare(Play o1, Play o2) {
                                return (int) Math.signum(o2.scr - o1.scr); //descending order
                            }
                        }
                );
                for (Play play : ret)
                    out.append(play.toString() + "\n");
            }
            return out.toString();
        }
        public ArrayList<Play> activePlayers() {
            ArrayList<Play> ret = new ArrayList<>();
            for (String p : ratings.keySet())
                if (!elims.contains(p))
                    ret.add(new Play(p, ratings.get(p)));
            ret.sort(
                    new Comparator<Play>() {
                        public int compare(Play o1, Play o2) {
                            return (int) Math.signum(o2.scr - o1.scr); //descending order
                        }
                    }
            );
            return ret;
        }
        public ArrayList<Play> eliminatedPlayers() {
            ArrayList<Play> ret = new ArrayList<>();
            for (String p : elims)
                ret.add(new Play(p, ratings.get(p)));
            ret.sort(
                    new Comparator<Play>() {
                        public int compare(Play o1, Play o2) {
                            return (int) Math.signum(o2.scr - o1.scr); //descending order
                        }
                    }
            );
            return ret;
        }
        public void update(ArrayList<Play> plays) {
            //update players' ratings
            //if score is negative infinity, that player is elims
            //if positive infinity, that player is rejoined
            //having infinite and finite scores in one game is not allowed
            boolean hasFinite=false, hasInfinite=false;
            for (Play p:plays) {
                if (Double.isInfinite(p.scr))
                    hasInfinite=true;
                else
                    hasFinite=true;
            }
            if (hasFinite && hasInfinite)
                throw new RuntimeException("Attempting to have eliminations/rejoins and plays at once.");
            if (hasInfinite) {
                for (Play p : plays)
                    if (Double.isInfinite(p.scr)) {
                        if (!ratings.containsKey(p.player))
                            throw new RuntimeException("Attempting to eliminate/rejoin unknown player "+p.player);
                        if (p.scr == Double.POSITIVE_INFINITY) {
                            //rejoin
                            if (!elims.contains(p.player))
                                throw new RuntimeException("Attempting to rejoin already rejoined player "+p.player);
                            elims.remove(p.player);
                        } else {
                            //eliminated
                            if (elims.contains(p.player))
                                throw new RuntimeException("Attempting to eliminate already eliminated player "+p.player);
                            elims.add(p.player);
                        }
                    }
                return;
            }
            //a round cannot have eliminated and non-eliminated players
            boolean hasElim=false, hasNonelim=false;
            for (Play p:plays) {
                if (elims.contains(p.player))
                    hasElim=true;
                else
                    hasNonelim=true;
            }
            if (hasElim && hasNonelim)
                throw new RuntimeException("Attempting to have eliminated and non-eliminated players play.");
            //new players are given an initial rating of 1000
            //check for new players
            for (Play p:plays)
                if (!ratings.containsKey(p.player))
                    ratings.put(p.player,1000.0);
            //take every pair and do rating update before changing actual rating
            //R'=R+KVAL*(S-E); R=rating, R'=new rating, S=1 if win 0 if lose, E=expected probability of winning
            HashMap<String,Double> changes=new HashMap<>();
            for (Play p:plays)
                changes.put(p.player,0.0);
            for (int i=1; i<plays.size(); i++) {
                if (Double.isInfinite(plays.get(i).scr))
                    continue;
                for (int j=0; j<i; j++) {
                    if (Double.isInfinite(plays.get(j).scr))
                        continue;
                    double  pi_diff=plays.get(i).scr-plays.get(j).scr;
                    /*if (pi_diff==0)
                        continue;*/
                    String pi=plays.get(i).player, pj=plays.get(j).player;
                    changes.put(pi,
                            changes.get(pi)
                                    +KVAL*((pi_diff>0?1:pi_diff<0?0:0.5)-winprob(plays.get(i).scr,plays.get(j).scr))
                    );
                    changes.put(pj,
                            changes.get(pj)
                                    +KVAL*((pi_diff>0?0:pi_diff<0?1:0.5)-winprob(plays.get(j).scr,plays.get(i).scr))
                    );
                }
            }
            for (String p:changes.keySet())
                ratings.put(p,ratings.get(p)+changes.get(p));
        }
    }
}