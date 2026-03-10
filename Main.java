package org.main;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class Main {

    public static void main(String[] args) {

        WebDriver driver = new ChromeDriver();

        driver.get("https://sig.ufla.br/modulos/publico/matrizes_curriculares/index.php");

        String codigoCurso = "G007";
        String codigoMatriz = "202202";

        selecionarCurso(driver, codigoCurso, codigoMatriz);

        int quantidade = extrairQuantidadePeriodos(driver);
        System.out.println("Quantidade de períodos: " + quantidade);

        Map<String, Disciplina> grafo = extrairGradeCompleta(driver);

        construirDependentes(grafo);

        calcularPeriodoMaximo(grafo, quantidade);

        imprimirPeriodoMaximo(grafo);

        driver.quit();
    }

    public static void selecionarCurso(WebDriver driver, String codigoCurso, String codigoMatriz) {

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("cod_oferta_curso")));

        Select selectCurso = new Select(driver.findElement(By.id("cod_oferta_curso")));

        selectCurso.selectByVisibleText(
                selectCurso.getOptions().stream()
                        .filter(option -> option.getText().contains(codigoCurso))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Curso não encontrado: " + codigoCurso))
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

        String textoCompleto = elemento.getText();
        String numero = textoCompleto.replaceAll("\\D+", "");

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

        if (tabelaModulos == null) {
            System.out.println("Tabela de módulos não encontrada.");
            return grafo;
        }

        List<WebElement> linhas = tabelaModulos.findElements(By.tagName("tr"));
        int periodoAtual = 0;

        for (WebElement linha : linhas) {

            String textoLinha = linha.getText().trim();

            if (textoLinha.toLowerCase().contains("módulo")) {
                periodoAtual = extrairNumeroPeriodo(textoLinha);
                continue;
            }

            List<WebElement> colunas = linha.findElements(By.tagName("td"));

            if (colunas.size() >= 7) {

                String codigo = colunas.get(0).getText().trim();

                if (codigo.isEmpty() || codigo.equalsIgnoreCase("Código"))
                    continue;

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
            String codigoReq = req.getText().trim();

            if (!codigoReq.isEmpty() && !codigoReq.equals("-")) {
                d.adicionarRequisito(codigoReq, tipo);
            }
        }
    }

    private static int extrairNumeroPeriodo(String texto) {
        String numero = texto.replaceAll("\\D+", "");
        return numero.isEmpty() ? 0 : Integer.parseInt(numero);
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

        Map<Disciplina, Integer> grauEntrada = new HashMap<>();
        Queue<Disciplina> fila = new LinkedList<>();
        List<Disciplina> ordem = new ArrayList<>();

        for (Disciplina d : grafo.values()) {

            int grau = 0;

            for (Requisito r : d.requisitos) {
                if (r.tipo != TipoRequisito.COREQUISITO) {
                    grau++;
                }
            }

            grauEntrada.put(d, grau);

            if (grau == 0) {
                fila.add(d);
            }
        }

        while (!fila.isEmpty()) {

            Disciplina atual = fila.poll();
            ordem.add(atual);

            for (Aresta aresta : atual.dependentes) {

                if (aresta.peso == 1) { // só reduz grau para forte/minimo
                    Disciplina dependente = aresta.destino;

                    int novoGrau = grauEntrada.get(dependente) - 1;
                    grauEntrada.put(dependente, novoGrau);

                    if (novoGrau == 0) {
                        fila.add(dependente);
                    }
                }
            }
        }

        if (ordem.size() != grafo.size()) {
            throw new RuntimeException("O grafo possui ciclo real de pré-requisitos fortes!");
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

            if (!d.dependentes.isEmpty()) {

                int menor = Integer.MAX_VALUE;

                for (Aresta aresta : d.dependentes) {

                    Disciplina dep = aresta.destino;
                    int peso = aresta.peso;

                    menor = Math.min(menor, dep.periodoMaximo - peso);
                }

                d.periodoMaximo = menor;
            }

            d.periodoMaximo = Math.max(d.periodoMaximo, d.periodo);
        }
    }

    public static void imprimirPeriodoMaximo(Map<String, Disciplina> grafo) {

        List<Disciplina> lista = new ArrayList<>(grafo.values());
        lista.sort((a, b) -> Integer.compare(a.periodo, b.periodo));

        System.out.println("\n PERÍODO MÁXIMO ");

        for (Disciplina d : lista) {
            System.out.println(
                    d.nome +
                            " | Original: " + d.periodo +
                            " | Máximo: " + d.periodoMaximo
            );
        }
    }
}

enum TipoRequisito {
    FORTE,
    MINIMO,
    COREQUISITO
}

class Requisito {
    String codigo;
    TipoRequisito tipo;

    public Requisito(String codigo, TipoRequisito tipo) {
        this.codigo = codigo;
        this.tipo = tipo;
    }
}

class Aresta {
    Disciplina destino;
    int peso; // 1 = forte/minimo | 0 = corequisito

    public Aresta(Disciplina destino, int peso) {
        this.destino = destino;
        this.peso = peso;
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

    public Disciplina(String codigo, String nome, int periodo, int creditos) {
        this.codigo = codigo;
        this.nome = nome;
        this.periodo = periodo;
        this.creditos = creditos;
    }

    public void adicionarRequisito(String codigo, TipoRequisito tipo) {
        requisitos.add(new Requisito(codigo, tipo));
    }

    @Override
    public String toString() {
        return codigo + " - " + nome + " (Período " + periodo + ")";
    }
}