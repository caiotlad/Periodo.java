package org.main;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.*;

import java.time.Duration;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.print("Digite o código do curso (ex: G010): ");
        String codigoCurso = sc.nextLine();

        System.out.print("Digite o código da matriz (ex: 202301): ");
        String codigoMatriz = sc.nextLine();

        WebDriver driver = new ChromeDriver();

        driver.get("https://sig.ufla.br/modulos/publico/matrizes_curriculares/index.php");

        selecionarCurso(driver, codigoCurso, codigoMatriz);

        int quantidade = extrairQuantidadePeriodos(driver);

        Map<String, Disciplina> grafo = extrairGradeCompleta(driver);

        //  SEPARA TCC
        List<Disciplina> tccs = extrairTCCs(grafo);

        //  INPUT
        EstadoAluno estado = lerEstadoAluno(grafo);
        propagarReprovacoes(grafo, estado);
        calcularConcluidas(grafo, estado);

        Configuracao config = lerConfiguracao();

        removerDisciplinasConcluidas(grafo, estado);

        construirDependentes(grafo);
        calcularPeriodoMaximo(grafo, quantidade);

        //  PLANO SEM TCC
        Map<Integer, List<Disciplina>> plano = montarPlano(grafo, estado, config);

        //  ADICIONA TCC
        adicionarTCCNoFinal(plano, tccs, config);

        imprimirPlano(plano);

        driver.quit();
    }

    //  TCC

    public static List<Disciplina> extrairTCCs(Map<String, Disciplina> grafo) {

        List<Disciplina> tccs = new ArrayList<>();

        Iterator<Map.Entry<String, Disciplina>> it = grafo.entrySet().iterator();

        while (it.hasNext()) {
            Disciplina d = it.next().getValue();

            if (ehTCC(d)) {
                tccs.add(d);
                it.remove();
            }
        }

        return tccs;
    }

    public static boolean ehTCC(Disciplina d) {
        String nome = d.nome.toLowerCase();
        return nome.contains("trabalho de conclusão")
                || nome.contains("tcc")
                || nome.contains("estágio supervisionado");
    }

    public static List<Disciplina> ordenarTCCs(List<Disciplina> tccs) {

        Map<String, Disciplina> mapa = new HashMap<>();
        for (Disciplina d : tccs) mapa.put(d.codigo, d);

        List<Disciplina> ordenado = new ArrayList<>();
        Set<String> feitas = new HashSet<>();

        while (ordenado.size() < tccs.size()) {

            for (Disciplina d : tccs) {

                if (feitas.contains(d.codigo)) continue;

                boolean pode = true;

                for (Requisito r : d.requisitos) {
                    if (r.tipo != TipoRequisito.COREQUISITO &&
                            mapa.containsKey(r.codigo) &&
                            !feitas.contains(r.codigo)) {

                        pode = false;
                        break;
                    }
                }

                if (pode) {
                    ordenado.add(d);
                    feitas.add(d.codigo);
                }
            }
        }

        return ordenado;
    }

    public static void adicionarTCCNoFinal(
            Map<Integer, List<Disciplina>> plano,
            List<Disciplina> tccs,
            Configuracao config
    ) {

        if (tccs.isEmpty()) return;

        List<Disciplina> ordenados = ordenarTCCs(tccs);

        int ultimoPeriodo = plano.isEmpty() ? 0 : Collections.max(plano.keySet());

        int periodo = ultimoPeriodo;

        // sem reserva → empilha no final respeitando ordem
        if (config.periodosTCC == 0) {

            for (Disciplina d : ordenados) {

                plano.putIfAbsent(periodo, new ArrayList<>());

                if (!plano.get(periodo).isEmpty()) {
                    periodo++;
                    plano.putIfAbsent(periodo, new ArrayList<>());
                }

                plano.get(periodo).add(d);
            }

            return;
        }

        // com reserva → força sequência no final
        periodo = ultimoPeriodo + 1;

        for (Disciplina d : ordenados) {

            plano.putIfAbsent(periodo, new ArrayList<>());
            plano.get(periodo).add(d);

            periodo++; // garante ordem (TCC1 → TCC2)
        }
    }

    //  PLANEJAMENTO

    public static Map<Integer, List<Disciplina>> montarPlano(
            Map<String, Disciplina> grafoOriginal,
            EstadoAluno estado,
            Configuracao config
    ) {

        Map<String, Disciplina> grafo = new HashMap<>(grafoOriginal);
        Map<Integer, List<Disciplina>> plano = new HashMap<>();

        Set<String> feitas = new HashSet<>(estado.concluidas);
        int periodoAtual = estado.ultimoPeriodoConcluido + 1;

        while (!grafo.isEmpty()) {

            List<Disciplina> disponiveis = new ArrayList<>();

            for (Disciplina d : grafo.values()) {
                if (podeCursar(d, feitas)) {
                    disponiveis.add(d);
                }
            }

            disponiveis.sort(Comparator.comparingInt(d -> d.periodoMaximo));

            int creditosAtual = 0;
            List<Disciplina> alocadas = new ArrayList<>();

            for (Disciplina d : disponiveis) {

                int custoTotal = d.creditos;

                // soma corequisitos
                for (Requisito r : d.requisitos) {
                    if (r.tipo == TipoRequisito.COREQUISITO) {
                        Disciplina co = grafo.get(r.codigo);
                        if (co != null && !alocadas.contains(co)) {
                            custoTotal += co.creditos;
                        }
                    }
                }

                if (creditosAtual + custoTotal > config.maxCreditosPorPeriodo)
                    continue;

                alocadas.add(d);
                creditosAtual += d.creditos;

                for (Requisito r : d.requisitos) {
                    if (r.tipo == TipoRequisito.COREQUISITO) {

                        Disciplina co = grafo.get(r.codigo);

                        if (co != null && !alocadas.contains(co)) {
                            alocadas.add(co);
                            creditosAtual += co.creditos;
                        }
                    }
                }
            }

            if (alocadas.isEmpty()) {
                alocadas.add(disponiveis.get(0));
            }

            plano.put(periodoAtual, alocadas);

            for (Disciplina d : alocadas) {
                feitas.add(d.codigo);
                grafo.remove(d.codigo);
            }

            periodoAtual++;
        }

        return plano;
    }

    public static boolean podeCursar(Disciplina d, Set<String> feitas) {

        for (Requisito r : d.requisitos) {

            if (r.tipo == TipoRequisito.COREQUISITO) continue;

            if (!feitas.contains(r.codigo)) return false;
        }

        return true;
    }

    //  INPUT / HISTÓRICO

    public static EstadoAluno lerEstadoAluno(Map<String, Disciplina> grafo) {

        Scanner sc = new Scanner(System.in);

        EstadoAluno estado = new EstadoAluno();

        System.out.print("Até qual período você concluiu? ");
        estado.ultimoPeriodoConcluido = sc.nextInt();
        sc.nextLine();

        System.out.println("\nMatérias até esse período:");

        for (Disciplina d : grafo.values()) {
            if (d.periodo <= estado.ultimoPeriodoConcluido) {
                System.out.println(d.codigo + " - " + d.nome);
            }
        }

        System.out.println("\nDigite os códigos das matérias que você NÃO concluiu:");

        String linha = sc.nextLine().trim();
        if (!linha.isEmpty()) {
            estado.reprovadas.addAll(Arrays.asList(linha.split(" ")));
        }

        return estado;
    }

    public static Configuracao lerConfiguracao() {

        Scanner sc = new Scanner(System.in);

        Configuracao config = new Configuracao();

        System.out.print("Máximo de créditos por período: ");
        config.maxCreditosPorPeriodo = sc.nextInt();

        System.out.print("Quantos períodos deseja reservar para TCC? ");
        config.periodosTCC = sc.nextInt();

        return config;
    }

    public static void propagarReprovacoes(Map<String, Disciplina> grafo, EstadoAluno estado) {

        Set<String> naoFeitas = new HashSet<>(estado.reprovadas);

        boolean mudou;

        do {
            mudou = false;

            for (Disciplina d : grafo.values()) {

                if (naoFeitas.contains(d.codigo)) continue;

                for (Requisito r : d.requisitos) {

                    if (r.tipo == TipoRequisito.COREQUISITO) continue;

                    if (naoFeitas.contains(r.codigo)) {
                        naoFeitas.add(d.codigo);
                        mudou = true;
                        break;
                    }
                }
            }

        } while (mudou);

        estado.reprovadas = naoFeitas;
    }

    public static void calcularConcluidas(Map<String, Disciplina> grafo, EstadoAluno estado) {

        for (Disciplina d : grafo.values()) {

            if (d.periodo <= estado.ultimoPeriodoConcluido &&
                    !estado.reprovadas.contains(d.codigo)) {

                estado.concluidas.add(d.codigo);
            }
        }
    }

    public static void removerDisciplinasConcluidas(Map<String, Disciplina> grafo, EstadoAluno estado) {

        for (String cod : estado.concluidas) {
            grafo.remove(cod);
        }

        for (Disciplina d : grafo.values()) {
            d.requisitos.removeIf(r -> estado.concluidas.contains(r.codigo));
        }
    }

    //  PRINT

    public static void imprimirPlano(Map<Integer, List<Disciplina>> plano) {

        System.out.println("\n PLANO GERADO \n");

        List<Integer> periodos = new ArrayList<>(plano.keySet());
        Collections.sort(periodos);

        for (int periodo : periodos) {

            System.out.println("Período " + periodo + ":");

            for (Disciplina d : plano.get(periodo)) {
                System.out.println("- " + d.codigo + " | " + d.nome + " (" + d.creditos + " créditos)");
            }

            System.out.println();
        }
    }

    // WEBSCRAPING

    public static void selecionarCurso(WebDriver driver, String codigoCurso, String codigoMatriz) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cod_oferta_curso")));

        Select selectCurso = new Select(driver.findElement(By.id("cod_oferta_curso")));

        selectCurso.selectByVisibleText(
                selectCurso.getOptions().stream()
                        .filter(option -> option.getText().contains(codigoCurso))
                        .findFirst()
                        .orElseThrow()
                        .getText()
        );

        driver.findElement(By.id("enviar")).click();

        WebElement matriz = wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[contains(., '" + codigoMatriz + "')]")
                )
        );
        matriz.click();
    }

    public static int extrairQuantidadePeriodos(WebDriver driver) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement elemento = wait.until(
                ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//fieldset[@id='drag_fieldset1']//p[strong[contains(text(),'Quantidade de Períodos')]]")
                )
        );

        String numero = elemento.getText().replaceAll("\\D+", "");
        return Integer.parseInt(numero);
    }

    public static Map<String, Disciplina> extrairGradeCompleta(WebDriver driver) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("table")));

        Map<String, Disciplina> grafo = new HashMap<>();

        List<WebElement> tabelas = driver.findElements(By.tagName("table"));

        WebElement tabelaModulos = null;

        for (WebElement tabela : tabelas) {
            if (tabela.getText().toLowerCase().contains("módulo")) {
                tabelaModulos = tabela;
                break;
            }
        }

        if (tabelaModulos == null) return grafo;

        List<WebElement> linhas = tabelaModulos.findElements(By.tagName("tr"));

        int periodoAtual = 0;

        for (WebElement linha : linhas) {

            String texto = linha.getText().trim();

            if (texto.toLowerCase().contains("módulo")) {
                periodoAtual = Integer.parseInt(texto.replaceAll("\\D+", ""));
                continue;
            }

            List<WebElement> colunas = linha.findElements(By.tagName("td"));

            if (colunas.size() >= 7) {

                String codigo = colunas.get(0).getText().trim();
                if (codigo.isEmpty() || codigo.equalsIgnoreCase("Código")) continue;

                String nome = colunas.get(1).getText().trim();

                int creditos = 0;
                try {
                    creditos = Integer.parseInt(colunas.get(2).getText().trim());
                } catch (Exception ignored) {}

                Disciplina d = new Disciplina(codigo, nome, periodoAtual, creditos);

                adicionarRequisitos(colunas.get(4), d, TipoRequisito.FORTE);
                adicionarRequisitos(colunas.get(5), d, TipoRequisito.MINIMO);
                adicionarRequisitos(colunas.get(6), d, TipoRequisito.COREQUISITO);

                grafo.put(codigo, d);
            }
        }

        return grafo;
    }

    private static void adicionarRequisitos(WebElement coluna, Disciplina d, TipoRequisito tipo) {

        List<WebElement> requisitos = coluna.findElements(By.tagName("abbr"));

        for (WebElement req : requisitos) {
            String codigo = req.getText().trim();
            if (!codigo.isEmpty() && !codigo.equals("-")) {
                d.adicionarRequisito(codigo, tipo);
            }
        }
    }

    public static void construirDependentes(Map<String, Disciplina> grafo) {

        for (Disciplina d : grafo.values()) {
            for (Requisito r : d.requisitos) {
                Disciplina prereq = grafo.get(r.codigo);
                if (prereq != null) {
                    int peso = (r.tipo == TipoRequisito.COREQUISITO) ? 0 : 1;
                    prereq.dependentes.add(new Aresta(d, peso));
                }
            }
        }
    }

    public static List<Disciplina> ordenacaoTopologica(Map<String, Disciplina> grafo) {

        Map<Disciplina, Integer> grau = new HashMap<>();
        Queue<Disciplina> fila = new LinkedList<>();
        List<Disciplina> ordem = new ArrayList<>();

        for (Disciplina d : grafo.values()) {

            int g = 0;

            for (Requisito r : d.requisitos) {
                if (r.tipo != TipoRequisito.COREQUISITO) g++;
            }

            grau.put(d, g);

            if (g == 0) fila.add(d);
        }

        while (!fila.isEmpty()) {

            Disciplina atual = fila.poll();
            ordem.add(atual);

            for (Aresta a : atual.dependentes) {

                if (a.peso == 1) {

                    Disciplina dep = a.destino;

                    int novo = grau.get(dep) - 1;
                    grau.put(dep, novo);

                    if (novo == 0) fila.add(dep);
                }
            }
        }

        return ordem;
    }

    public static void calcularPeriodoMaximo(Map<String, Disciplina> grafo, int totalPeriodos) {

        List<Disciplina> ordem = ordenacaoTopologica(grafo);

        for (Disciplina d : grafo.values()) {
            d.periodoMaximo = totalPeriodos;
        }

        for (int i = ordem.size() - 1; i >= 0; i--) {

            Disciplina d = ordem.get(i);

            int menor = Integer.MAX_VALUE;

            for (Aresta a : d.dependentes) {
                menor = Math.min(menor, a.destino.periodoMaximo - a.peso);
            }

            if (menor != Integer.MAX_VALUE)
                d.periodoMaximo = Math.max(menor, d.periodo);
        }
    }
}

//  CLASSES

class EstadoAluno {
    int ultimoPeriodoConcluido;
    Set<String> concluidas = new HashSet<>();
    Set<String> reprovadas = new HashSet<>();
}

class Configuracao {
    int maxCreditosPorPeriodo;
    int periodosTCC;
}

enum TipoRequisito {
    FORTE, MINIMO, COREQUISITO
}

class Requisito {
    String codigo;
    TipoRequisito tipo;

    public Requisito(String c, TipoRequisito t) {
        codigo = c;
        tipo = t;
    }
}

class Aresta {
    Disciplina destino;
    int peso;

    public Aresta(Disciplina d, int p) {
        destino = d;
        peso = p;
    }
}

class Disciplina {

    String codigo;
    String nome;
    int periodo;
    int creditos;

    List<Requisito> requisitos = new ArrayList<>();
    List<Aresta> dependentes = new ArrayList<>();

    int periodoMaximo;

    public Disciplina(String c, String n, int p, int cred) {
        codigo = c;
        nome = n;
        periodo = p;
        creditos = cred;
    }

    public void adicionarRequisito(String codigo, TipoRequisito tipo) {
        requisitos.add(new Requisito(codigo, tipo));
    }
}