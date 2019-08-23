package com.example.research_httpsocket.field_changing_research;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.UnaryOperator;

/**
 * @author Konstantin Epifanov
 * @since 23.08.2019
 */
public class FieldHolder implements UnaryOperator<Runnable> {

  private String field;

  final private CopyOnWriteArraySet<Runnable> onChangedListeners  = new CopyOnWriteArraySet<>();

  public FieldHolder(String field) {
    this.field = field;
  }

  public void setField(String field) {
    this.field = field;
    notifyFieldChanged();
  }

  public String getField() {
    return field;
  }

  protected void notifyFieldChanged() {
    for (Runnable runnable : onChangedListeners) runnable.run();
  }

  public void addOnFieldChangedListener(Runnable listener) {
    onChangedListeners.add(listener);
  }

  public void removeOnFieldChangedListener(Runnable victim) {
    onChangedListeners.remove(victim);
  }



  /* TODO USE THIS */
  public Runnable registerListener(Runnable runnable) {
    onChangedListeners.add(runnable);
    return () -> onChangedListeners.remove(runnable); // Runnable == dispose
  }

  @Override
  public Runnable apply(Runnable runnable) {
    onChangedListeners.add(runnable);
    return () -> onChangedListeners.remove(runnable);
  }
}
