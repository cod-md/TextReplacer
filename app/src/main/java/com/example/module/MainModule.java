private void hookInputConnectionMethods(Class<?> icClass) {
        // Only hook each discovered class once
        if (hookedClasses.contains(icClass)) {
            return;
        }
        hookedClasses.add(icClass);

        // LOG 1: See what class Gboard is actually using
        log(4, "TextReplacer", ">>> DISCOVERED REAL CLASS: " + icClass.getName());

        try {
            // 2. Find and hook commitText
            Method commitText = findMethodInHierarchy(icClass, "commitText", CharSequence.class, int.class);
            if (commitText != null) {
                log(4, "TextReplacer", ">>> FOUND commitText IN: " + commitText.getDeclaringClass().getName());
                
                hook(commitText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()) {
                                    Object firstArg = args.get(0);
                                    if (firstArg instanceof CharSequence) {
                                        String typedText = firstArg.toString();
                                        
                                        // LOG 2: Watch everything Gboard commits
                                        log(4, "TextReplacer", "[commitText] Gboard sent: '" + typedText + "'");
                                        
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            log(4, "TextReplacer", "[commitText] MATCH FOUND! Replacing text.");
                                            String newText = typedText.replaceAll("(?i)fuck", "its a bad word");
                                            args.set(0, newText);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, "TextReplacer", "Error inside commitText hook", t);
                            }
                            return chain.proceed();
                        });
            } else {
                log(6, "TextReplacer", ">>> COULD NOT FIND commitText in hierarchy!");
            }

            // 3. Find and hook setComposingText
            Method setComposingText = findMethodInHierarchy(icClass, "setComposingText", CharSequence.class, int.class);
            if (setComposingText != null) {
                log(4, "TextReplacer", ">>> FOUND setComposingText IN: " + setComposingText.getDeclaringClass().getName());
                
                hook(setComposingText)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                List<Object> args = chain.getArgs();
                                if (args != null && !args.isEmpty()) {
                                    Object firstArg = args.get(0);
                                    if (firstArg instanceof CharSequence) {
                                        String typedText = firstArg.toString();
                                        
                                        // LOG 3: Watch everything Gboard is composing (underlined text)
                                        log(4, "TextReplacer", "[setComposingText] Gboard composing: '" + typedText + "'");
                                        
                                        if (typedText.toLowerCase().contains("fuck")) {
                                            log(4, "TextReplacer", "[setComposingText] MATCH FOUND! Replacing text.");
                                            String newText = typedText.replaceAll("(?i)fuck", "its a bad word");
                                            args.set(0, newText);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                log(6, "TextReplacer", "Error inside setComposingText hook", t);
                            }
                            return chain.proceed();
                        });
            } else {
                log(6, "TextReplacer", ">>> COULD NOT FIND setComposingText in hierarchy!");
            }

        } catch (Throwable t) {
            log(6, "TextReplacer", "Failed to dynamically hook methods for " + icClass.getName(), t);
        }
    }
