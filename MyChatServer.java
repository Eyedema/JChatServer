package unifi.inf.rc.UbaldoPuocci;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings(value = { "resource" })
public class MyChatServer implements Runnable {

	private Map<String, String> map;
	private InetAddress indirizzo;
	private int porta;
	private ArrayList<String> listaTopic;
	private ArrayList<Message> listaMessaggi;
	private int countMessaggi = -1;
	private Lock lock;
	private Map<String, RegisterTuple> register;
	private Map<String, ArrayList<Integer>> subscribe;
	private Map<String, Integer> digest;
	private Map<String, ArrayList<Message>> digestList;

	public MyChatServer(Map<String, String> map, InetAddress indirizzo, int porta) {
		this.map = map;
		this.indirizzo = indirizzo;
		this.porta = porta;
		listaTopic = new ArrayList<String>();
		listaMessaggi = new ArrayList<Message>();
		lock = new ReentrantLock();
		register = new HashMap<>();
		subscribe = new HashMap<>();
		digest = new HashMap<>();
		digestList = new HashMap<>();
		initDigest(map);
	}

	private void initDigest(Map<String, String> map) {
		for (Map.Entry<String, String> entry : map.entrySet()) {
			digest.put(entry.getKey(), 1);
			digestList.put(entry.getKey(), new ArrayList<Message>());
		}
	}

	@Override
	public void run() {
		ServerSocket server = null;
		try {
			server = new ServerSocket(porta, 10, indirizzo);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.out.println("server waiting for connections...");
		while (true) {
			System.out.println("starting on address: " + indirizzo + ":" + porta);
			try {
				Socket client = server.accept();
				System.out.println("new connection");
				InputStream read = client.getInputStream();
				OutputStream write = client.getOutputStream();
				Scanner scanner = new Scanner(new InputStreamReader(read)).useDelimiter("\\r\\n");
				Runnable thread = () -> {
					try {
						clientHandler(scanner, client, write);
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				};
				new Thread(thread).start();
			} catch (IOException e) {
				e.printStackTrace();

			}
		}
	}

	private void clientHandler(Scanner scanner, Socket client, OutputStream write)
			throws UnsupportedEncodingException, IOException {
		String stringa_ricevuta = "";
		int userMandato = 0;
		String nomeUtente = "";
		String password = "";
		final int AUTENTICATO = 1;
		int utente = 0;
		while (true) {
			try {
				stringa_ricevuta = parseCommand(scanner, stringa_ricevuta);
			} catch (Exception e1) {
				return;
			}
			String[] comando = stringa_ricevuta.split(" ");
			String funzione = comando[0];
			if (userMandato == 0 && utente != AUTENTICATO && !funzione.equals("USER")) {
				send(write, "KO\r\n");
			} else if (utente != AUTENTICATO && funzione.equals("USER") && nomeUtente.isEmpty()) {
				send(write, "OK\r\n");
				userMandato = 1;
				nomeUtente = getParameter(comando, 1, comando.length);
			} else if (utente != AUTENTICATO && funzione.equals("PASS") && userMandato == 1) {
				password = getParameter(comando, 1, comando.length);
				if (map.containsKey(nomeUtente) && map.get(nomeUtente).equals(password)) {
					send(write, "OK\r\n");
					utente = AUTENTICATO;
				} else {
					send(write, "KO\r\n");
					userMandato = 0;
					nomeUtente = "";
					password = "";
				}
			} else if (utente == AUTENTICATO) {
				try {
					lock.lock();
					String ret = commandSwitcher(comando, nomeUtente, stringa_ricevuta);
					try {
						send(write, ret);
					} catch (Exception e) {
					}
				} finally {
					lock.unlock();
				}

			} else {
				send(write, "KO\r\n");
			}

		}
	}

	private String parseCommand(Scanner scanner, String stringa_ricevuta) {
		stringa_ricevuta = scanner.next();
		if (stringa_ricevuta.startsWith("MESSAGE") || stringa_ricevuta.startsWith("REPLY")) {
			int tmp = 0;
			while (tmp < 3) {
				try {
					stringa_ricevuta += " -" + scanner.next();
				} catch (Exception e) {
				}
				tmp++;
			}
		}
		return stringa_ricevuta;
	}

	private String commandSwitcher(String[] comando, String nomeUtente, String stringa_comando) {
		String funzione = comando[0];
		switch (funzione) {
		case "NEW":
			return newTopic(comando);
		case "TOPICS":
			return topicList(comando, nomeUtente);
		case "MESSAGE":
			return newMessage(comando, nomeUtente);
		case "LIST":
			return messageList(comando);
		case "GET":
			return getMessage(comando);
		case "REPLY":
			return newAnswer(comando, nomeUtente);
		case "CONV":
			return getConversation(comando);
		case "REGISTER":
			return register(comando, nomeUtente);
		case "UNREGISTER":
			return unregister(comando, nomeUtente);
		case "SUBSCRIBE":
			return subscribe(comando, nomeUtente);
		case "UNSUBSCRIBE":
			return unsubscribe(comando, nomeUtente);
		case "DIGEST":
			return digest(comando, nomeUtente);
		default:
			return "KO\r\n";
		}
	}

	private String digest(String[] comando, String nomeUtente) {
		if (register.get(nomeUtente) == null) {
			return "KO\r\n";
		}
		int k = 0;
		try {
			k = Integer.parseInt(comando[1]);
		} catch (Exception e) {
			return "KO\r\n";
		}
		digest.put(nomeUtente, k);
		return "OK\r\n";
	}

	private String unsubscribe(String[] comando, String nomeUtente) {
		if (comando.length == 1) {
			return "KO\r\n";
		}
		ArrayList<Integer> tid = new ArrayList<Integer>();
		for (int i = 1; i < comando.length; i++) {
			try {
				tid.add(Integer.parseInt(comando[i]));
			} catch (NumberFormatException e) {
				return "KO\r\n";
			}
		}
		if (register.get(nomeUtente) == null || listaTopic.size() == 0 || subscribe.get(nomeUtente) == null) {
			for (int i = 0; i < tid.size(); i++) {
				if (!listaTopic.contains(tid.get(i))) {
					return "KO\r\n";
				}
			}
			return "KO\r\n";
		}
		subscribe.put(nomeUtente, removeFromList(subscribe.get(nomeUtente), tid));
		return "OK\r\n";
	}

	private String subscribe(String[] comando, String nomeUtente) {
		if (comando.length == 1) {
			return "KO\r\n";
		}
		ArrayList<Integer> tid = new ArrayList<Integer>();
		for (int i = 1; i < comando.length; i++) {
			try {
				tid.add(Integer.parseInt(comando[i]));
			} catch (NumberFormatException e) {
				return "KO\r\n";
			}
		}
		if (register.get(nomeUtente) == null || listaTopic.size() == 0) {
			for (int i = 0; i < tid.size(); i++) {
				if (!listaTopic.contains(tid.get(i))) {
					return "KO\r\n";
				}
			}
			return "KO\r\n";
		}
		for (int i = 1; i < comando.length; i++) {
			try {
				tid.add(Integer.parseInt(comando[i]));
			} catch (NumberFormatException e) {
				return "KO\r\n";
			}
		}
		if (subscribe.get(nomeUtente) == null) {
			subscribe.put(nomeUtente, tid);
		} else {
			subscribe.put(nomeUtente, joinList(subscribe.get(nomeUtente), tid));
		}
		subscribe.put(nomeUtente, joinList(subscribe.get(nomeUtente), tid));
		return "OK\r\n";
	}

	private ArrayList<Integer> joinList(ArrayList<Integer> toAddTo, ArrayList<Integer> iterateOver) {
		for (Integer num : iterateOver) {
			if (toAddTo.indexOf(num) == -1) {
				toAddTo.add(num);
			}
		}
		return toAddTo;
	}

	private ArrayList<Integer> removeFromList(ArrayList<Integer> toRemoveTo, ArrayList<Integer> iterateOver) {
		for (Integer num : iterateOver) {
			if (toRemoveTo.indexOf(num) != -1) {
				toRemoveTo.remove(num);
			}
		}
		return toRemoveTo;
	}

	private String unregister(String[] comando, String nomeUtente) {
		if (comando.length > 1) {
			return "KO\r\n";
		}
		if (register.get(nomeUtente) == null) {
			return "KO\r\n";
		}
		register.remove(nomeUtente);
		subscribe.remove(nomeUtente);
		return "OK\r\n";
	}

	private String register(String[] comando, String nomeUtente) {
		if (comando.length < 3) {
			return "KO\r\n";
		}
		String host = comando[1];
		int port = 0;
		try {
			port = Integer.parseInt(comando[2]);
			if (port > 65535) {
				return "KO\r\n";
			}
		} catch (NumberFormatException e) {
			return "KO\r\n";
		}
		RegisterTuple tmp = null;
		try {
			tmp = new RegisterTuple(InetAddress.getByName(host), port);
		} catch (UnknownHostException e) {
		}
		for (Map.Entry<String, RegisterTuple> entry : register.entrySet()) {
			if (!entry.getKey().equals(nomeUtente) && entry.getValue().equals(tmp)) {
				return "KO\r\n";
			}
			if (register.get(nomeUtente) != null) {
				register.remove(nomeUtente);
			}
		}
		for (Map.Entry<String, RegisterTuple> entry : register.entrySet()) {
			if (!entry.getKey().equals(nomeUtente) && entry.getValue().equals(tmp)) {
				return "KO\r\n";
			}
		}
		register.put(nomeUtente, tmp);
		return "OK\r\n";
	}

	private String getConversation(String[] comando) {
		int mid = 0;
		try {
			mid = Integer.parseInt(comando[1]);
		} catch (Exception e) {
			return "KO\r\n";
		}
		Message messaggio = listaMessaggi.get(mid);
		int numeroPadre = messaggio.getParent();
		ArrayList<Message> testa = new ArrayList<Message>();
		testa.add(messaggio);
		while (numeroPadre != -1) {
			messaggio = listaMessaggi.get(numeroPadre);
			testa.add(messaggio);
			numeroPadre = messaggio.getParent();
		}
		Collections.reverse(testa);
		mid = Integer.parseInt(comando[1]);
		messaggio = listaMessaggi.get(mid);
		ArrayList<Integer> listaFigli = messaggio.getChildren();
		if (!listaFigli.isEmpty()) {
			for (int i = 0; i < listaFigli.size(); i++) {
				recursionChild(listaFigli.get(i), listaMessaggi.get(listaFigli.get(i)), testa);
			}
		}
		String ret = "MESSAGES\r\n";
		Collections.sort(testa, Comparator.comparing(Message::getCount));
		ArrayList<String> toret = fromMessageToString(testa);
		ret += fromListToString(toret, true) + "\r\n";
		return ret;
	}

	private ArrayList<String> fromMessageToString(ArrayList<Message> testa) {
		ArrayList<String> toret = new ArrayList<String>();
		for (int i = 0; i < testa.size(); i++) {
			String tmp = testa.get(i).getCount() + " " + testa.get(i).getMittente() + " "
					+ fromListToString(testa.get(i).getTopics(), false) + "\r\n";
			toret.add(tmp);
		}
		return toret;
	}

	private void recursionChild(int figlio, Message messaggio, ArrayList<Message> testa) {
		ArrayList<Integer> listaFigli = listaMessaggi.get(figlio).getChildren();
		if (!testa.contains(messaggio)) {
			testa.add(messaggio);
		}
		if (!listaFigli.isEmpty()) {
			for (int i = 0; i < listaFigli.size(); i++) {
				recursionChild(listaFigli.get(i), listaMessaggi.get(listaFigli.get(i)), testa);
			}
		}
	}

	private String newAnswer(String[] comando, String nomeUtente) {
		int mid = 0;
		try {
			mid = Integer.parseInt(comando[1]);
		} catch (Exception e) {
			return "KO\r\n";
		}
		String testoMessaggio = getParameter(comando, 2, comando.length - 2).substring(1);
		Message messaggio = null;
		try {
			messaggio = listaMessaggi.get(mid);
		} catch (Exception e) {
			return "KO\r\n";
		}
		countMessaggi++;
		messaggio.addChild(countMessaggi);
		Message reply = new Message(countMessaggi, testoMessaggio, messaggio.getTopics(), nomeUtente, mid);

		checkSubscription(nomeUtente, reply);
		listaMessaggi.add(reply);
		return "OK " + countMessaggi + "\r\n";
	}

	private ArrayList<Message> addMessageToList(Message e, ArrayList<Message> l) {
		l.add(e);
		return l;
	}

	private void checkSubscription(String nomeUtente, Message reply) {
		for (Entry<String, ArrayList<Integer>> entry : subscribe.entrySet()) {
			if (topicsInMexAndTList(entry.getValue(), reply.getTopics())) {
				digestList.put(entry.getKey(), addMessageToList(reply, digestList.get(entry.getKey())));
			}
		}
		for (Entry<String, Integer> entry : digest.entrySet()) {
			if (digest.get(entry.getKey()) <= digestList.get(entry.getKey()).size()
					&& register.get(entry.getKey()) != null) {
				registerMessage(entry.getKey(), digestList.get(entry.getKey()), entry.getValue());
			}
		}

	}

	private String getMessage(String[] comando) {
		if (comando.length < 2) {
			return "KO\r\n";
		}
		int mid = 0;
		try {
			mid = Integer.parseInt(comando[1]);
		} catch (NumberFormatException e) {
			return "KO\r\n";
		}
		if (mid > listaMessaggi.size() - 1 || mid < 0) {
			return "KO\r\n";
		}
		Message messaggio = listaMessaggi.get(mid);
		String testa = "MESSAGE " + mid + "\r\nUSER " + messaggio.getMittente() + "\r\nTOPICS "
				+ fromListToString(messaggio.getTopics(), false) + "\r\n" + messaggio.getTesto() + "\r\n.\r\n\r\n";
		return testa;
	}

	private String messageList(String[] comando) {
		if (comando.length < 2) {
			return "KO\r\n";
		}
		int mid = 0;
		try {
			mid = Integer.parseInt(comando[1]);
		} catch (NumberFormatException e) {
			return "KO\r\n";
		}
		ArrayList<Integer> tlist = new ArrayList<Integer>();
		for (int i = 2; i < comando.length; i++) {
			try {
				int tmp = Integer.parseInt(comando[i]);
				if (tmp > listaTopic.size() - 1) {
					return "KO\r\n";
				}
				tlist.add(tmp);
			} catch (NumberFormatException e) {
				return "KO\r\n";
			}
		}
		String testa = "MESSAGES\r\n";
		if (tlist.size() != 0) {
			for (int i = 0; i < listaMessaggi.size(); i++) {
				if (listaMessaggi.get(i).getCount() >= mid
						&& topicsInMexAndTList(listaMessaggi.get(i).getTopics(), tlist)) {
					testa += listaMessaggi.get(i).getCount() + " " + listaMessaggi.get(i).getMittente() + " "
							+ fromListToString(listaMessaggi.get(i).getTopics(), false) + "\r\n";
				}
			}
		} else {
			for (int i = 0; i < listaMessaggi.size(); i++) {
				if (listaMessaggi.get(i).getCount() >= mid) {
					testa += listaMessaggi.get(i).getCount() + " " + listaMessaggi.get(i).getMittente() + " "
							+ fromListToString(listaMessaggi.get(i).getTopics(), false) + "\r\n";
				}
			}
		}
		testa += "\r\n";
		return testa;
	}

	private <T> String fromListToString(ArrayList<T> list, boolean conv) {
		StringBuilder strbul = new StringBuilder();
		Iterator<T> iter = list.iterator();
		while (iter.hasNext()) {
			strbul.append(iter.next());
			if (iter.hasNext() && !conv) {
				strbul.append(" ");
			}
		}
		return strbul.toString();
	}

	private boolean topicsInMexAndTList(ArrayList<Integer> tlistMessaggio, ArrayList<Integer> tlistComando) {
		ArrayList<Integer> result = new ArrayList<Integer>(tlistComando);
		result.retainAll(tlistMessaggio);
		return !result.isEmpty();
	}

	private String newMessage(String[] comando, String nomeUtente) {
		String testoMessaggio = "";
		ArrayList<Integer> mid = new ArrayList<Integer>();
		int tmp = 0;
		for (int i = 1; i < comando.length; i++) {
			try {
				if (comando[i].startsWith("-")) {
					break;
				}
				mid.add(Integer.parseInt(comando[i]));
				tmp++;
			} catch (NumberFormatException e) {
				break;
			}
		}
		for (int i = 0; i < mid.size(); i++) {
			if (mid.get(i) > listaTopic.size() - 1) {
				return "KO\r\n";
			}
		}
		testoMessaggio = (getParameter(comando, tmp + 1, comando.length - 2)).substring(1);
		if (mid.size() == 0 || testoMessaggio.isEmpty()) {
			return "KO\r\n";
		}
		countMessaggi++;
		Message messaggio = new Message(countMessaggi, testoMessaggio, mid, nomeUtente, -1);
		checkSubscription(nomeUtente, messaggio);
		listaMessaggi.add(messaggio);
		return "OK " + countMessaggi + "\r\n";
	}

	private void registerMessage(String nomeUtente, ArrayList<Message> messaggi, int k) {
		Socket socket = null;
		OutputStream out = null;
		try {
			socket = new Socket(register.get(nomeUtente).getHost(), register.get(nomeUtente).getPort());
			out = socket.getOutputStream();
			String testa = "";
			Iterator<Message> it = messaggi.iterator();
			int tmp = k;
			while (tmp > 0) {
				if (!testa.equals("")) {
					testa += "\r\n";
				}
				Message messaggio = it.next();
				testa += "MESSAGE " + messaggio.getCount() + "\r\nUSER " + messaggio.getMittente() + "\r\nTOPICS "
						+ fromListToString(messaggio.getTopics(), false) + "\r\n" + messaggio.getTesto() + "\r\n";
				it.remove();
				tmp--;
			}
			it.forEachRemaining(messaggi::add);
			digestList.put(nomeUtente, messaggi);
			if (k > 1) {
				testa += "\r\n.\r\n\r\n";
			} else {
				testa += ".\r\n\r\n";
			}
			send(out, testa);
			out.flush();
			socket.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String topicList(String[] comando, String nomeUtente) {
		if (comando.length > 1) {
			return "KO\r\n";
		}
		String testa = "TOPIC_LIST\r\n";
		if (listaTopic.size() > 0) {
			for (int i = 0; i < listaTopic.size(); i++) {
				if (subscribe.get(nomeUtente) != null && subscribe.get(nomeUtente).contains(i)) {
					testa += "*";
				}
				testa += i + " " + listaTopic.get(i) + "\r\n";
			}
		}
		testa += "\r\n";
		return testa;
	}

	private String newTopic(String[] comando) {
		String nomeTopic = getParameter(comando, 1, comando.length);
		if (nomeTopic.isEmpty()) {
			return "KO\r\n";
		}
		listaTopic.add(nomeTopic);
		return "OK " + (listaTopic.size() - 1) + "\r\n";

	}

	private String getParameter(String[] comando, int start, int end) {
		StringJoiner sj = new StringJoiner(" ");
		String parameter = "";
		for (int i = start; i < end; i++) {
			sj.add(comando[i]);
		}
		parameter = sj.toString();
		return parameter;
	}

	private void send(OutputStream write, String s) throws IOException, UnsupportedEncodingException {
		try {
			write.write(s.getBytes("latin1"));
		} catch (Exception e) {
		}
	}

}
