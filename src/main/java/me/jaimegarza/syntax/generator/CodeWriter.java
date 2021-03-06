/*
Syntax is distibuted under the Revised, or 3-clause, BSD license
===================================================================
Copyright (c) 1985, 2012, Jaime Garza
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
 * Neither the name of Jaime Garza nor the
names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
===================================================================
 */
package me.jaimegarza.syntax.generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import me.jaimegarza.syntax.OutputException;
import me.jaimegarza.syntax.definition.Action;
import me.jaimegarza.syntax.definition.GoTo;
import me.jaimegarza.syntax.definition.NonTerminal;
import me.jaimegarza.syntax.definition.State;
import me.jaimegarza.syntax.env.Environment;

/**
 * Phases:
 * 
 * <ol>
 *   <li>Code Parser
 *   <li>Structural Analysis
 *   <li>Table Generation
 *   <li><b>Writing Code</b> (This Phase)
 * </ol>
 *
 * @author jaimegarza@gmail.com
 *
 */
public class CodeWriter extends AbstractPhase {

  private static final char RESOURCE_SEPARATOR = '/';

  /**
   * Construct a code writer out of the shared environment
   * @param environment is the global environment
   */
  public CodeWriter(Environment environment) {
    super(environment);
  }

  /**
   * Open code with the declarations
   */
  private void printHeader() {
    environment.language.printCodeHeader();
  }

  /**
   * prints the code for an unpacked table row 
   * @param state is the state to print
   */
  private void printTableRow(State state) {
    int parserLine[] = state.getRow();
    int stateNumber = state.getId();

    if (environment.isPacked() == true) {
      return;
    }
    int symbolCounter = runtimeData.getTerminals().size() + runtimeData.getNonTerminals().size() - 2;
    environment.language.printTableRow(symbolCounter, parserLine, stateNumber);
  }

  /**
   * print multiple tables
   */
  private void printTables() {
    int stateNumber, action, error;

    if (environment.isPacked() == true) {
      environment.language.printActionHeader();
      action = 0;
      for (stateNumber = 0; stateNumber < runtimeData.getStates().length; stateNumber++) {
        if (runtimeData.getStates()[stateNumber].getPosition() >= action) {
          for (Action anAction : runtimeData.getStates()[stateNumber].getActions()) {
            environment.language.printAction(action, anAction);
            action++;
          }
        }
      }

      environment.language.printGoToTableHeader();
      int gotoIndex = 0;
      for (NonTerminal id : runtimeData.getNonTerminals()) {
        if (id.getGotos() != null && id.getGotos().size() > 0) {
          for (GoTo pGoto : id.getGotos()) {
            environment.language.printGoTo(gotoIndex++, pGoto);
          }
        }
      }
      
      environment.language.printParsingTableHeader();
      for (stateNumber = 0; stateNumber < runtimeData.getStates().length; stateNumber++) {
        environment.language.printPackedState(stateNumber);
      }
    }
    if (environment.isPacked() == false) {
      environment.language.printParserErrors();
    }
    environment.language.printErrorTableHeader();
    for (error = 0; error < runtimeData.getErrorMessages().size(); error++) {
      environment.language.printErrorEntry(error);
    }
    environment.language.printErrorFooter();
  }

  /**
   * Close the printing of code
   */
  private void printFooter() {
    printTables();
    environment.language.printGrammarTable();
  }
  
  /**
   * Given the defined arguments (and defaults) compute the skeleton name
   * @return the skeleton in the resources
   */
  private String getSkeletonResourceName() {
    StringBuilder builder = new StringBuilder();
    String parserStructure = environment.isPacked() ? "packed" : "tabular";
    builder.append(environment.getDriver().skeleton())
           .append(RESOURCE_SEPARATOR)
           .append(parserStructure)
           .append(RESOURCE_SEPARATOR)
           .append(parserStructure).append(environment.language.getExtensionSuffix());
    
    return builder.toString();
  }

  /**
   * Close the output by putting the remaining of the grammar file and the skeleton parser
   * @throws IOException
   */
  private void finishOutput() throws IOException {
    String filename = getSkeletonResourceName();

    ClassLoader loader = this.getClass().getClassLoader();
    InputStream is = loader.getResourceAsStream(filename);
    if (is != null) {
      try {
        if (environment.isVerbose()) {
          System.out.println("using skeleton " + filename);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        environment.language.emitLine(1, filename);
        environment.language.emitLine(1, filename);
        String line = reader.readLine();
        while (line != null) {
          environment.output.println(line);
          line = reader.readLine();
        }
      } finally {
        is.close();
      }
    } else {
      System.err.println("\n\nWarning: internal skeleton \"" + filename + "\" not found.  Table was generated.\n");
      environment.language.printMissingSkeleton(filename);
    }

    if (runtimeData.hasFinalActions() == false) {
      return;
    }

    environment.output.println();
    environment.language.emitLine(runtimeData.lineNumber + 1);

    int c = environment.source.read();
    while (c != -1) {
      environment.output.print((char) c);
      c = environment.source.read();
    }
  }

  private void reportSummary() {
    environment.report.println();
    environment.report.println("Summary:");
    environment.report.println("=============================================================================================================");
    environment.report.println("Source:           " + environment.getSourceFile());
    environment.report.println("Output:           " + environment.getOutputFile());
    environment.report.println("Include/Interface:" + environment.getIncludeFile());
    environment.report.println("Algorithm:        " + environment.getAlgorithmType());
    environment.report.println("Language:         " + environment.getLanguageEnum());
    environment.report.println("Packed?:          " + environment.isPacked());
    environment.report.println("Tokens:           " + runtimeData.getTerminals().size());
    environment.report.println("Non Terminals:    " + runtimeData.getNonTerminals().size());
    environment.report.println("Types:            " + runtimeData.getTypes().size());
    environment.report.println("Rules:            " + runtimeData.getRules().size());
    environment.report.println("Errors:           " + runtimeData.getErrorMessages().size());
    environment.report.println("Actions:          " + runtimeData.getNumberOfActions());
    environment.report.println("Gotos:            " + runtimeData.getNumberOfGoTos());
    environment.report.println("Recoveries:       " + runtimeData.getNumberOfRecoveries());
    environment.report.println("States:           " + runtimeData.getStates().length);
    environment.report.println("=============================================================================================================");
    environment.report.println();
  }
  
  /**
   * Execute all the elements of this phase
   * @throws OutputException on error
   */
  public void execute() throws OutputException {
    try {
      printHeader();
      if (environment.isPacked() == false) {
        for (int i = 0; i < runtimeData.getStates().length; i++) {
          printTableRow(runtimeData.getStates()[i]);
        }
      }
      printFooter();
      finishOutput();
      reportSummary();
    } catch (IOException e) {
      throw new OutputException("Error while creating output files", e);
    }
  }
}
