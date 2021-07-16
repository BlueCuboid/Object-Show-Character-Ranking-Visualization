import java.io.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import javax.imageio.ImageIO;
public class Ranker2 {
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
        private static final int NORMAL=0, ELIM=-1, REJOIN=1, DEAD=-2, REVIVE=2;
        int type;
        ArrayList<Play> plays;
        String name, episode;
        public Round(String name, String episode, ArrayList<Play> plays) {
            this.type=NORMAL;
            this.name=name;
            this.episode=episode;
            this.plays=new ArrayList<>(plays);
        }
        public Round(int type, String episode, ArrayList<Play> plays) {
            this.type=type;
            this.name="";
            this.episode=episode;
            this.plays=new ArrayList<>(plays);
        }
        public String toString() {
            return type+" "+name+" "+episode+" "+plays;
        }
    }
    private static Map<String,Integer> colors(String file) throws IOException {
        Scanner sc=new Scanner(new File(file));
        Map<String,Integer> out=new HashMap<>();
        while (sc.hasNext()) {
            String line=sc.nextLine();
            int split=line.lastIndexOf(" ");
            out.put(line.substring(0,split),Integer.parseInt(line.substring(split+1),16));
        }
        return out;
    }
    private static boolean prefix(String pre, String str) {
        return str.length()>=pre.length() && str.substring(0,pre.length()).equals(pre);
    }
    private static ArrayList<Round> rounds(String data) throws IOException {
        Scanner sc=new Scanner(new File(data));
        ArrayList<Round> out=new ArrayList<>();
        ArrayList<Play> plays=new ArrayList<>();
        String round_name="", ep="";
        double scr=Double.POSITIVE_INFINITY;
        final String RND="#", EP="ep", ELIM="e-", REJOIN="!e-", DEAD="d-", REVIVE="!d-";
        final String[] specials={RND,EP,ELIM,REJOIN,DEAD,REVIVE};
        while (sc.hasNext()) {
            String line=sc.nextLine();
            if (line.length()==0 || prefix("//",line))
                continue;
            String s=null;
            for (String p:specials)
                if (prefix(p,line)) {
                    s=p;
                    break;
                }
            if (s!=null) {
                if (plays.size()>0)
                    out.add(new Round(round_name,ep,plays));
                plays.clear();
                if (s.equals(RND))
                    round_name=line.substring(1);
                else if (s.equals(EP))
                    ep=line.substring(2);
                else {
                    ArrayList<Play> contestants=new ArrayList<>();
                    String[] ppl=line.substring(s.length()).split(",");
                    for (String p:ppl)
                        contestants.add(new Play(p,0));
                    out.add(new Round(
                            s.equals(ELIM)?Round.ELIM:
                                s.equals(REJOIN)?Round.REJOIN:
                                s.equals(DEAD)?Round.DEAD:
                                Round.REVIVE,
                            ep,contestants
                    ));
                }
            }
            else {
                try {
                    scr = Double.parseDouble(line);
                } catch (Exception e) {
                    //not a number: line is the name of a player
                    String[] ps=line.split(",");
                    for (String p:ps)
                        plays.add(new Play(p, scr));
                }
            }
        }
        if (plays.size()>0)
            out.add(new Round(round_name,ep,plays));
        return out;
    }
    private static class Vis extends JPanel {
        private static final int XMIN=85, XMAX=1350, YMIN=50, YMAX=720;
        private static final double RATESPACELO=150, RATESPACEHI=50, MSPACE=100;
        //taken from Baeldung (https://www.baeldung.com/java-resize-image)
        private BufferedImage resized(BufferedImage img0, int w, int h) {
            BufferedImage img1 = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            img1.getGraphics().drawImage(img0.getScaledInstance(w, h, Image.SCALE_DEFAULT), 0, 0, null);
            return img1;
        }
        private static double interp(double a, double b, double alpha) {
            return a+(b-a)*alpha;
        }
        private static double interp(double[] pair, double alpha) {
            return interp(pair[0],pair[1],alpha);
        }
        private static double minval(Collection<Double> S) {
            double out=Double.POSITIVE_INFINITY;
            for (double t:S)
                out=Math.min(out,t);
            return out;
        }
        private static double maxval(Collection<Double> S) {
            double out=Double.NEGATIVE_INFINITY;
            for (double t:S)
                out=Math.max(out,t);
            return out;
        }
        private ArrayList<Play> activeRatings;
        private Map<String,Integer> orank;
        private Map<String,Double> orate;
        private Map<String,double[][]> dinfo;
        private String episode;
        private double dplayercnt, alpha, maxRating, minRating;
        public boolean startcard;
        private Map<String,Integer> colors;
        private Map<String,BufferedImage[]> imgs;
        public Vis(Map<String,Integer> colors) throws IOException {
            activeRatings=new ArrayList<>();
            orank=new HashMap<>();
            orate=new HashMap<>();
            dinfo=new HashMap<>();
            this.colors=colors;
            startcard=true;
            imgs=new HashMap<>();
            for (String name:colors.keySet()) { //somewhat slow
                BufferedImage m=ImageIO.read(
                        new File("BFDI\\voting_icons\\"+name.replace("-","").replace(" ","").replace(".","")
                                +"_TeamIcon.png")
                );
                BufferedImage[] ms=new BufferedImage[96];
                for (int i=1; i<=95; i++)
                    ms[i]=resized(m,i,i);
                imgs.put(name,ms);
            }
        }
        public void paintRatings(Ratings r, String ep, long millis) {
            episode=ep;
            activeRatings=r.activePlayers();
            Map<String,Integer> nrank=new HashMap<>();
            Map<String,Double> nrate=new HashMap<>();
            for (int i=0; i<activeRatings.size(); i++) {
                Play p=activeRatings.get(i);
                nrank.put(p.player,i);
                nrate.put(p.player,p.scr);
            }
            Set<String> onplayers=new HashSet<>(orank.keySet());
            onplayers.addAll(nrank.keySet());
            dinfo.clear();
            for (String p:onplayers) {
                double or, nr, ot, nt;
                if (orank.containsKey(p)) {
                    or=orank.get(p);
                    ot=orate.get(p);
                }
                else {
                    or=orank.size()+1;
                    ot=0;
                }
                if (nrank.containsKey(p)) {
                    nr=nrank.get(p);
                    nt=nrate.get(p);
                }
                else {
                    nr=nrank.size()+1;
                    nt=orate.get(p);
                }
                dinfo.put(p,new double[][] {
                        new double[] {or,nr},
                        new double[] {ot,nt},
                        new double[] {!orank.containsKey(p)?1:!nrank.containsKey(p)?-1:0}
                });
            }
            double omaxRating=orate.keySet().size()==0?0:maxval(orate.values()),
                    ominRating=orate.keySet().size()==0?0:minval(orate.values());
            double nmaxRating=maxval(nrate.values()),
                    nminRating=minval(nrate.values());
            for (long t=System.currentTimeMillis(), tend=t+millis;
                 t<=tend; t=System.currentTimeMillis()) {
                alpha=1-(double)(tend-t)/millis;
                dplayercnt=interp(orate.size(),nrate.size(),alpha);
                maxRating=RATESPACEHI+interp(omaxRating,nmaxRating,alpha);
                minRating=-RATESPACELO+interp(ominRating,nminRating,alpha);
                repaint();
            }
            alpha=1;
            dplayercnt=nrate.size();
            maxRating=RATESPACEHI+nmaxRating;
            minRating=-RATESPACELO+nminRating;
            repaint();
            orank=nrank;
            orate=nrate;
        }
        public void paintComponent(Graphics g) {
            paint2D((Graphics2D)g);
        }
        private void paint2D(Graphics2D g2) {
            AffineTransform orig=g2.getTransform();
            g2.setColor(Color.BLACK);
            g2.fillRect(0,0,1400,1400);
            if (startcard) return;
            int W=XMAX-XMIN, H=YMAX-YMIN;
            double bar=W/dplayercnt;
            //draw scale
            g2.setFont(new Font("Helvetica", Font.PLAIN, 14));
            g2.setColor(Color.GRAY);
            for (double m=Math.ceil(minRating/MSPACE)*MSPACE; m<=maxRating; m+=MSPACE) {
                int yloc=YMAX-(int)((m-minRating)/(maxRating-minRating)*H);
                g2.fillRect(XMIN-5,yloc,W+5,1);
                g2.drawString(""+(int)m,XMIN-7-g2.getFontMetrics(g2.getFont()).stringWidth(""+(int)m),yloc+4);
            }
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Helvetica", Font.PLAIN, dplayercnt<=20?24:18));
            for (String name:dinfo.keySet()) {
                double rank=interp(dinfo.get(name)[0],alpha),
                        rating=interp(dinfo.get(name)[1],alpha);
                int estat=(int)dinfo.get(name)[2][0];
                double fade=estat==-1?(1-alpha):1;
                int A=(int)(255*fade);
                int colcode=colors.get(name);
                Color col=new Color((colcode>>16),(colcode>>8)&0xff,colcode&0xff,A);
                Color fwhite=new Color(255,255,255,A);
                boolean dark=Color.RGBtoHSB(col.getRed(),col.getGreen(),col.getBlue(),null)[2]<0.25;
                g2.setColor(col);
                int rectc=(int)(XMIN+rank*bar),
                        rectl=(int)((rating-minRating)/(maxRating-minRating)*H),
                        rectw=(int)(XMIN+(rank+1)*bar)-rectc;
                g2.fillRect(rectc,YMAX-rectl,rectw,rectl);
                int imgsz=Math.max(1,Math.min(rectw,95));
                g2.drawImage(imgs.get(name)[imgsz],
                        rectc+rectw-imgsz,YMAX-rectl,null);
                if (dark) {
                    g2.setColor(fwhite);
                    g2.drawRect(rectc,YMAX-rectl,rectw,rectl);
                }
                g2.rotate(-Math.PI/2);
                g2.drawString(""+(int)rating,rectl-YMAX+2,rectc+rectw-5);
                g2.setColor(dark?fwhite:new Color(0,0,0,A));
                g2.drawString(name,rectl-YMAX-imgsz-g2.getFontMetrics(g2.getFont()).stringWidth(name)-5,rectc+rectw-5);
                g2.setTransform(orig);
            }
            g2.setColor(Color.WHITE);
            if (episode!=null) {
                g2.setFont(new Font("Helvetica", Font.PLAIN, 48));
                g2.drawString("BFB "+episode,
                        (XMIN+XMAX)/2-g2.getFontMetrics(g2.getFont()).stringWidth("BFB "+episode)/2,
                        YMIN);
            }
            g2.rotate(-Math.PI/2);
            g2.setFont(new Font("Helvetica", Font.PLAIN, 36));
            g2.drawString("Contest Rating",
                    -(YMIN+YMAX)/2-g2.getFontMetrics(g2.getFont()).stringWidth("Contest Rating")/2,
                    XMIN-50);
            g2.setTransform(orig);
            g2.setColor(Color.BLACK);
            g2.fillRect(0,YMAX,1400,300);
        }
    }
    public static void main(String[] args) throws IOException {
        ArrayList<Round> rounds= rounds("BFDI\\bfb.txt");
        Ratings bfdi=new Ratings();
        JFrame w = new JFrame("BFB Rankings");
        w.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container c = w.getContentPane();
        Vis visual=new Vis(colors("BFDI\\bfb_colors.txt"));
        c.add(visual);
        w.setBounds(0,0,1400,700);
        w.setVisible(true);
        //waitms(3000);
        visual.startcard=false;
        visual.repaint();
        waitms(3000);
        for (int i=0; i<rounds.size();) {
            String ep=rounds.get(i).episode;
            //System.out.println("ep="+ep);
            for (; i<rounds.size() && rounds.get(i).episode.equals(ep); i++) {
                bfdi.update(rounds.get(i));
                //System.out.println(rounds.get(i));
            }
            visual.paintRatings(bfdi,ep,3000);
            waitms(3000);
        }
    }
    private static void waitms(long ms) {
        for (long TOUT=System.currentTimeMillis()+ms; System.currentTimeMillis()<TOUT;);
    }
    private static class Ratings {
        //a list of players with their contest ratings
        private Map<String,Double> ratings;
        private Set<String> elims, deads;
        private static final double DEFAULT=1000;
        private static double winprob(double ra, double rb) {
            //probability of player with rating ra beating another player with rating rb
            return 1/(1+Math.pow(10,(rb-ra)/400));
        }
        public Ratings() {
            ratings=new HashMap<>();
            elims=new HashSet<>();
            deads=new HashSet<>();
        }
        private static void sortPlays(ArrayList<Play> ret) {
            ret.sort(new Comparator<Play>() {
                public int compare(Play o1, Play o2) {
                    return (int)Math.signum(o2.scr-o1.scr);
                }
            });
        }
        public String toString() {
            StringBuilder out=new StringBuilder();
            for (int type=0; type<2; type++) {
                out.append((type==0?"Playing:":"Eliminated:")).append("\n");
                ArrayList<Play> ret = new ArrayList<>();
                for (String p : ratings.keySet())
                    if ((type==0)==!elims.contains(p))
                        ret.add(new Play(p, ratings.get(p)));
                sortPlays(ret);
                for (Play play : ret)
                    out.append(play.toString()).append("\n");
            }
            return out.toString();
        }
        public ArrayList<Play> activePlayers() {
            ArrayList<Play> ret = new ArrayList<>();
            for (String p : ratings.keySet())
                if (!elims.contains(p))
                    ret.add(new Play(p, ratings.get(p)));
            sortPlays(ret);
            return ret;
        }
        public void update(Round round) {
            ArrayList<Play> plays=round.plays;
            if (round.type!=Round.NORMAL) {
                if (round.type==Round.ELIM)
                    for (Play p:plays) elims.add(p.player);
                else if (round.type==Round.REJOIN)
                    for (Play p:plays) elims.remove(p.player);
                else if (round.type==Round.DEAD)
                    for (Play p:plays) deads.add(p.player);
                else if (round.type==Round.REVIVE)
                    for (Play p:plays) deads.remove(p.player);
                return;
            }
            //make sure no eliminated/dead/disabled contestants are competing
            for (Play p:plays)
            if (elims.contains(p.player))
                throw new RuntimeException("Player "+p.player+" is eliminated.");
            else if (deads.contains(p.player))
                throw new RuntimeException("Player "+p.player+" is dead/disabled.");
            //new players are given an initial default rating
            for (Play p:plays)
                if (!ratings.containsKey(p.player))
                    ratings.put(p.player,DEFAULT);
            //use rating change algorithm similar to that of Codeforces
            Map<String,Double> rranks=new HashMap<>();
            //rranks.get(p)==number of players who scored less than or equal to p
            //this is slightly different from how Codeforces defines rank
            sortPlays(plays);
            for (int i=0, j=0; i<plays.size(); i++) {
                while (j<plays.size()&&plays.get(j).scr>plays.get(i).scr)
                    j++;
                rranks.put(plays.get(i).player,(double)plays.size()-j);
            }
            /*for (String p:ratings.keySet())
                if (!elims.contains(p)&&!deads.contains(p)&&!rranks.keySet().contains(p))
                    System.out.println("episode "+round.episode+": "+p+"???");*/
            Map<String,Double> changes=new HashMap<>();
            for (String p:rranks.keySet()) {
                double erk=exprank(p,ratings.get(p),rranks.keySet()),
                        rrk=rranks.get(p),
                        brate=bestrating(p,Math.sqrt(erk*rrk),rranks.keySet());
                changes.put(p,(brate-ratings.get(p))/2);
            }
            //this section is commented out, because it ended up deflating ratings too much
            /*{ //force the rating changes of all competing contestants to sum to 0 or less
                double sum=0;
                for (String p:changes.keySet())
                    sum+=changes.get(p);
                double shift=-sum/rranks.size()-1;
                for (String p:changes.keySet())
                    changes.put(p,changes.get(p)+shift);
            }*/
            { //force the rating changes of the top s competing contestants to sum to 0,
                // in order to combat rating inflation
                int s=Math.min(rranks.size(),(int)(4*Math.sqrt(rranks.size())));
                ArrayList<String> ordered_players=new ArrayList<>(rranks.keySet());
                ordered_players.sort(new Comparator<String>() {
                    public int compare(String o1, String o2) {
                        return (int)Math.signum(ratings.get(o2)-ratings.get(o1));
                    }
                });
                double sum=0;
                for (int i=0; i<s; i++)
                    sum+=changes.get(ordered_players.get(i));
                double shift=Math.min(Math.max(-sum/s,-10),0);
                for (String p:changes.keySet())
                    changes.put(p,changes.get(p)+shift);
            }
            for (String p:changes.keySet())
                ratings.put(p,ratings.get(p)+changes.get(p));
        }
        private double exprank(String p, double r, Set<String> competed) {
            //expected rank of player p against all other players who competed, if player p had a rating of r
            double out=1;
            for (String q:competed)
            if (!q.equals(p))
                out+=winprob(r,ratings.get(q));
            return out;
        }
        private double bestrating(String p, double targetrank, Set<String> competed) {
            //approximate R, the rating such that exprank(p,R)==targetrank
            double lo=0, hi=8000;
            while (hi-lo>0.01) {
                double m=(lo+hi)/2;
                if (exprank(p,m,competed)<targetrank)
                    lo=m;
                else
                    hi=m;
            }
            return (lo+hi)/2;
        }
    }
}