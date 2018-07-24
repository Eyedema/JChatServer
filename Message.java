package unifi.inf.rc.UbaldoPuocci;

import java.util.ArrayList;

public class Message {

	private int count;
	private String testo;
	private ArrayList<Integer> topics;
	private ArrayList<Integer> figli;
	private String mittente;
	private int padre;

	public Message(int count, String testo, ArrayList<Integer> mid, String mittente, int padre) {
		this.count = count;
		this.testo = testo;
		this.topics = mid;
		this.mittente = mittente;
		this.padre = padre;
		figli = new ArrayList<Integer>();
	}

	@Override
	public String toString() {
		return "Message [count=" + count + ", testo=" + testo + ", topics=" + topics + ", figli=" + figli
				+ ", mittente=" + mittente + ", padre=" + padre + "]";
	}

	public int getCount() {
		return count;
	}

	public String getTesto() {
		return testo;
	}

	public ArrayList<Integer> getTopics() {
		return topics;
	}

	public ArrayList<Integer> getChildren() {
		return figli;
	}
	
	public void addChild(int k){
		figli.add(k);
	}

	public String getMittente() {
		return mittente;
	}

	public int getParent() {
		return padre;
	}
}