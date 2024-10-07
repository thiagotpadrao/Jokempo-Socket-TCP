package jokempo.rede;

import java.io.*;
import java.net.*;

import jokempo.jogo.Jogo;
import jokempo.jogo.Jogada;
import jokempo.jogo.Rodada;
import jokempo.utils.Mensagens;

//classe para gerenciar cada cliente conectado em uma thread separada, possibilitando múltiplos jogadores
public class Gerenciador implements Runnable{
	private Socket clienteSocket;
	private BufferedReader in;
	private PrintWriter out;
	private int playerid;
	private static Jogo jogo = new Jogo();
	private static Jogada jogadaplayer1;
	private static Jogada jogadaplayer2;
	private static PrintWriter msgplayer1;
	private static PrintWriter msgplayer2;
	
	public Gerenciador (Socket socket) {
        this.clienteSocket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream()));
            out = new PrintWriter(clienteSocket.getOutputStream(), true);
            conectarJogador(out);
        } catch (IOException e) {
            System.err.println(Mensagens.X_STREAMS + e.getMessage());
        } 
    }
	
	public void run() {
        try {
            String input;
            while ((input = in.readLine()) != null) {
                try {
                    processarJogada(input);
                    synchronized (jogo) {
                        if (jogadaplayer1 != null && jogadaplayer2 != null) {
                        	resolverRodada();
                            if (jogo.isGameOver()) {
                                encerrarJogo();
                                break;
                            }
                            resetarJogadas();
                            solicitarJogada();
                        }
                    }
                } catch (IllegalArgumentException e) {
                    out.println(Mensagens.X_MOVE);
                }
            }
        } catch (IOException e) {
            if (!clienteSocket.isClosed()) {
                System.err.println(Mensagens.X_COMUN + e.getMessage());
            }
        } finally {
        	fecharConexao();
        }
    }
	
	private void comunicarEspera() {
	    if (playerid == 1) {
	        if (jogadaplayer2 == null) msgplayer1.println(Mensagens.WAIT);
	        if (msgplayer2 != null && jogadaplayer2 == null) msgplayer2.println(Mensagens.WAIT2);
	    } else {
	        if (jogadaplayer1 == null) msgplayer2.println(Mensagens.WAIT);
	        if (msgplayer1 != null && jogadaplayer1 == null) msgplayer1.println(Mensagens.WAIT2);
	    }
	}
	
	private void processarJogada(String input) throws IllegalArgumentException {
	    Jogada jogadaplayer = Jogada.valueOf(input.toUpperCase());
	    synchronized (jogo) {
	        if (playerid == 1 && jogadaplayer1 == null) {
	            jogadaplayer1 = jogadaplayer;
	            comunicarEspera();
	        } else if (playerid == 2 && jogadaplayer2 == null) {
	            jogadaplayer2 = jogadaplayer;
	            comunicarEspera();
	        }
	    }
	}
	
	private void fecharConexao() {
        try {
            clienteSocket.close();
        } catch (IOException e) {
            System.err.println(Mensagens.X_SOCKET + e.getMessage());
        }
        ServidorJokempo.removeClient(clienteSocket);
	}
	
	private void resolverRodada() {
    	Rodada resultadoplayer1 = jogo.jogar(jogadaplayer1, jogadaplayer2);
        Rodada resultadoplayer2 = (resultadoplayer1 == Rodada.VITORIA) ? Rodada.DERROTA : (resultadoplayer1 == Rodada.DERROTA ? Rodada.VITORIA : Rodada.EMPATE);
        enviaResultadoSv(resultadoplayer1, resultadoplayer2);
        enviaResultado(msgplayer1, resultadoplayer1);
        enviaResultado(msgplayer2, resultadoplayer2);
	}
	
	private void resetarJogadas() {
        jogadaplayer1 = null;
        jogadaplayer2 = null;
	}
	
	public void conectarJogador(PrintWriter out) {
        synchronized (jogo) {
            if (msgplayer1 == null) {
                msgplayer1 = out;
                playerid = 1;
                msgplayer1.println(Mensagens.CONEC1);
            } else if (msgplayer2 == null) {
                msgplayer2 = out;
                playerid = 2;
                msgplayer2.println(Mensagens.CONEC2);
                msgplayer1.println(Mensagens.CONECATT);
                System.out.println(Mensagens.START);
                solicitarJogada();
            }
        }
	}
	
	private void enviaResultado(PrintWriter msgplayer, Rodada resultado) {
    	if (resultado == Rodada.VITORIA) {
            msgplayer.println(Mensagens.VENCEU);
        } else if (resultado == Rodada.DERROTA) {
            msgplayer.println(Mensagens.PERDEU);
        } else {
            msgplayer.println(Mensagens.EMPATE);
        }
	}
	
	private void enviaResultadoSv(Rodada resultado1, Rodada resultado2) {
        if (resultado1 == Rodada.VITORIA) {
        	System.out.println(Mensagens.JOGOU1 + jogadaplayer1 + Mensagens.JOGOU2 + jogadaplayer2 + Mensagens.GANHOU1 + jogo.getPontosplayer1() + " X " + jogo.getPontosplayer2() + Mensagens.C2);
        } else if (resultado2 == Rodada.VITORIA) {
        	System.out.println(Mensagens.JOGOU1 + jogadaplayer1 + Mensagens.JOGOU2 + jogadaplayer2 + Mensagens.GANHOU2 + jogo.getPontosplayer1() + " X " + jogo.getPontosplayer2() + Mensagens.C2);
        } else {
        	System.out.println(Mensagens.JOGOU1 + jogadaplayer1 + Mensagens.JOGOU2 + jogadaplayer2 + Mensagens.EMPT + jogo.getPontosplayer1() + " X " + jogo.getPontosplayer2() + Mensagens.C2);
        }
	}
	
    private void solicitarJogada() {
        jogo.incrementarRodada();
        int rodadaAtual = jogo.getNumeroRodada();
        int placarPlayer1 = jogo.getPontosplayer1();
        int placarPlayer2 = jogo.getPontosplayer2();
        if (msgplayer1 != null) {
            enviarSolicitacaoDeJogada(msgplayer1, placarPlayer1, placarPlayer2, rodadaAtual);
        }
        if (msgplayer2 != null) {
            enviarSolicitacaoDeJogada(msgplayer2, placarPlayer2, placarPlayer1, rodadaAtual);
        }  
    }
    
    private void enviarSolicitacaoDeJogada(PrintWriter msgPlayer, int placar, int placarOponente, int rodadaAtual) {
        msgPlayer.println(Mensagens.RODADA + rodadaAtual + Mensagens.FORMAT);
        msgPlayer.println(Mensagens.PLACAR + placar + " x " + placarOponente + Mensagens.OPONENTE);
        msgPlayer.println(Mensagens.JOGADA);
    }


    private void encerrarJogo() {
        int placarPlayer1 = jogo.getPontosplayer1();
        int placarPlayer2 = jogo.getPontosplayer2();
        if (placarPlayer1 >= 3) {
            enviarMensagemFinal(msgplayer1, Mensagens.VCVENCEU, placarPlayer1, placarPlayer2);
            enviarMensagemFinal(msgplayer2, Mensagens.VCPERDEU, placarPlayer2, placarPlayer1);
        } else if (placarPlayer2 >= 3) {
            enviarMensagemFinal(msgplayer2, Mensagens.VCVENCEU, placarPlayer2, placarPlayer1);
            enviarMensagemFinal(msgplayer1, Mensagens.VCPERDEU, placarPlayer1, placarPlayer2);
        }
        encerrarParaTodos();
        ServidorJokempo.removeBothClients();
    }
    
    private void enviarMensagemFinal(PrintWriter jogador, String resultado, int placar, int placarOponente) {
        jogador.println(resultado);
        jogador.println(Mensagens.PLACARFINAL + placar + " x " + placarOponente + Mensagens.OPONENTE);
    }
    
    private void encerrarParaTodos() {
        msgplayer1.println(Mensagens.ENDGAME);
        msgplayer2.println(Mensagens.ENDGAME);
        System.out.println(Mensagens.ENDGAMESV);
    }
    	
	public void sendMessage(String message) {
		out.println(message);
	}
}